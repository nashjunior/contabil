-- Wiring empenho -> assinatura (ADR-0027, RAZ-103): estado forte do
-- DOCUMENTO da nota de empenho — ortogonal ao comprometimento contábil, que
-- já é efetivo em `status = 'registrado'` (R1). Nunca gate a
-- escrituração/saldo por este status.
--
-- 1. `empenho` ganha `status` + `documento_pendente_uri` (ALTER, não
--    recriação — mesma disciplina de V6).
-- 2. `execucao_empenho_documento_assinado`: DOCUMENTO_ASSINADO (ADR-0027 (c))
--    com FK a empenho — append-only, um por empenho em F1.
-- 3. `execucao_empenho_documento_outbox`: outbox transacional dedicado do
--    evento `execucao_empenho_pendente_documento` (ADR-0027 (a)/(b)) — mesma
--    forma de `outbox_mensagem` (V4), tabela própria porque o consumidor
--    (`EmpenhoDocumentoPendenteWorker`) processa localmente (gera PDF, grava
--    no object store, transiciona o empenho), não despacha a um broker
--    externo.

-- 1. Estado forte do documento em empenho.
alter table empenho add column status text not null default 'registrado'
  check (status in ('registrado', 'pendente_assinatura', 'assinado', 'assinatura_rejeitada'));
alter table empenho add column documento_pendente_uri text;

-- 2. DOCUMENTO_ASSINADO (ADR-0027 (c)/(d)) — FK ao empenho; um por empenho em
--    F1 (retrabalho após ASSINATURA_REJEITADA reassina a MESMA nota pendente,
--    sem novo registro aqui).
create table execucao_empenho_documento_assinado (
  id                uuid not null default uuid_generate_v4(),
  ente_id           uuid not null references ente(id),
  empenho_id        uuid not null,
  uri_pdf_assinado  text not null,
  hash_sha256       text not null,
  manifesto         text not null,
  transacao_id      uuid not null,
  nivel_assinatura  text not null,
  signatario_cpf    text not null,
  assinado_em       timestamptz not null,
  criado_em         timestamptz not null default clock_timestamp(),
  primary key (id),
  unique (ente_id, id),
  unique (ente_id, empenho_id),
  foreign key (ente_id, empenho_id) references empenho (ente_id, id)
);

alter table execucao_empenho_documento_assinado enable row level security;
alter table execucao_empenho_documento_assinado force row level security;

create policy tenant_isolation on execucao_empenho_documento_assinado
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

revoke all on execucao_empenho_documento_assinado from public;
grant select, insert on execucao_empenho_documento_assinado to app_role;

-- 3. Outbox dedicado de `execucao_empenho_pendente_documento`.
create table execucao_empenho_documento_outbox (
  id                    uuid not null default uuid_generate_v4(),
  ente_id               uuid not null references ente(id),
  empenho_id            uuid not null,
  status                text not null default 'pendente'
                          check (status in ('pendente', 'retentando', 'concluido', 'falha_permanente')),
  tentativas            int not null default 0 check (tentativas >= 0),
  proxima_tentativa_em  timestamptz not null default clock_timestamp(),
  bloqueado_ate         timestamptz,
  ultimo_erro           text,
  concluido_em          timestamptz,
  criado_em             timestamptz not null default clock_timestamp(),
  atualizado_em         timestamptz not null default clock_timestamp(),
  primary key (id),
  unique (ente_id, id),
  unique (ente_id, empenho_id),                          -- idempotência do enqueue (ADR-0004/0011)
  foreign key (ente_id, empenho_id) references empenho (ente_id, id)
);
create index ix_execucao_empenho_documento_outbox_status
  on execucao_empenho_documento_outbox (status, proxima_tentativa_em, criado_em);

alter table execucao_empenho_documento_outbox enable row level security;
alter table execucao_empenho_documento_outbox force row level security;

create policy tenant_isolation on execucao_empenho_documento_outbox
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

-- Funções SECURITY DEFINER: superfície estreita do worker (roda sem
-- `app.ente_id` setado, fora de qualquer `executar(..)` advisado) para
-- atravessar a RLS só no ciclo operacional do outbox — mesmo padrão de
-- `outbox_entrega_*` (V4).
create function execucao_empenho_documento_outbox_reclamar(p_limite int, p_bloqueado_ate timestamptz)
returns table (
  id uuid,
  ente_id uuid,
  empenho_id uuid,
  tentativas int
)
language sql
security definer
set search_path = pg_catalog, public
as $$
  with selecionadas as (
    select o.id
      from execucao_empenho_documento_outbox o
     where o.status in ('pendente', 'retentando')
       and o.proxima_tentativa_em <= clock_timestamp()
       and (o.bloqueado_ate is null or o.bloqueado_ate <= clock_timestamp())
     order by o.criado_em, o.id
     for update skip locked
     limit least(greatest(p_limite, 1), 1000)
  ),
  atualizadas as (
    update execucao_empenho_documento_outbox o
       set bloqueado_ate = p_bloqueado_ate,
           atualizado_em = clock_timestamp()
      from selecionadas s
     where o.id = s.id
     returning o.id, o.ente_id, o.empenho_id, o.tentativas
  )
  select * from atualizadas;
$$;

create function execucao_empenho_documento_outbox_confirmar(p_id uuid)
returns void
language sql
security definer
set search_path = pg_catalog, public
as $$
  update execucao_empenho_documento_outbox
     set status = 'concluido',
         bloqueado_ate = null,
         ultimo_erro = null,
         concluido_em = clock_timestamp(),
         atualizado_em = clock_timestamp()
   where id = p_id
     and status in ('pendente', 'retentando');
$$;

create function execucao_empenho_documento_outbox_retentativa(p_id uuid, p_proxima_tentativa_em timestamptz, p_erro text)
returns void
language sql
security definer
set search_path = pg_catalog, public
as $$
  update execucao_empenho_documento_outbox
     set status = 'retentando',
         tentativas = tentativas + 1,
         proxima_tentativa_em = p_proxima_tentativa_em,
         bloqueado_ate = null,
         ultimo_erro = left(p_erro, 4000),
         atualizado_em = clock_timestamp()
   where id = p_id
     and status in ('pendente', 'retentando');
$$;

create function execucao_empenho_documento_outbox_dlq(p_id uuid, p_erro text)
returns void
language sql
security definer
set search_path = pg_catalog, public
as $$
  update execucao_empenho_documento_outbox
     set status = 'falha_permanente',
         tentativas = tentativas + 1,
         bloqueado_ate = null,
         ultimo_erro = left(p_erro, 4000),
         atualizado_em = clock_timestamp()
   where id = p_id
     and status in ('pendente', 'retentando');
$$;

revoke all on execucao_empenho_documento_outbox from public;
revoke all on function
  execucao_empenho_documento_outbox_reclamar(int, timestamptz),
  execucao_empenho_documento_outbox_confirmar(uuid),
  execucao_empenho_documento_outbox_retentativa(uuid, timestamptz, text),
  execucao_empenho_documento_outbox_dlq(uuid, text)
from public;

grant select, insert, update on execucao_empenho_documento_outbox to app_role;
grant execute on function
  execucao_empenho_documento_outbox_reclamar(int, timestamptz),
  execucao_empenho_documento_outbox_confirmar(uuid),
  execucao_empenho_documento_outbox_retentativa(uuid, timestamptz, text),
  execucao_empenho_documento_outbox_dlq(uuid, text)
to app_role;
