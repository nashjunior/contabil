-- Outbox de publicação/entrega garantida (ServicoEntrega) — RAZ-9.
-- Ref.: ADR-0004 (outbox idempotente), ADR-0011 (idempotência ponta a ponta)
-- e plataforma/plataforma-domain/entrega/ServicoEntrega.java.
--
-- O enqueue acontece na mesma transação do fato; o despacho externo é feito
-- depois pelo worker. As funções SECURITY DEFINER abaixo são a superfície
-- estreita do worker para atravessar a RLS forçada apenas no ciclo operacional
-- do outbox (claim/ack/retry/DLQ), sem liberar SELECT cross-tenant genérico.

create table outbox_mensagem (
  id          uuid not null default uuid_generate_v4(),
  ente_id     uuid not null references ente(id),
  chave       text not null,
  destino     text not null,
  tipo        text not null,
  conteudo    text not null,
  status      text not null default 'enfileirado'
                check (status in ('enfileirado','duplicado','retentando','entregue','falha_permanente')),
  tentativas  int not null default 0 check (tentativas >= 0),
  proxima_tentativa_em timestamptz not null default clock_timestamp(),
  bloqueado_ate timestamptz,
  ultimo_erro text,
  entregue_em timestamptz,
  criado_em   timestamptz not null default clock_timestamp(),
  atualizado_em timestamptz not null default clock_timestamp(),
  primary key (id),
  unique (ente_id, id),
  unique (ente_id, chave)                                -- idempotência do enqueue (ADR-0004/0011)
);
create index ix_outbox_mensagem_status on outbox_mensagem (status, proxima_tentativa_em, criado_em);

create table outbox_dlq (
  id          uuid not null default uuid_generate_v4(),
  mensagem_id uuid not null unique,
  ente_id     uuid not null references ente(id),
  chave       text not null,
  destino     text not null,
  tipo        text not null,
  conteudo    text not null,
  tentativas  int not null,
  erro        text not null,
  criado_em   timestamptz not null default clock_timestamp(),
  primary key (id),
  foreign key (ente_id, mensagem_id) references outbox_mensagem (ente_id, id)
);
create index ix_outbox_dlq_ente_criado on outbox_dlq (ente_id, criado_em);

alter table outbox_mensagem enable row level security;
alter table outbox_mensagem force row level security;
alter table outbox_dlq enable row level security;
alter table outbox_dlq force row level security;

create policy tenant_isolation on outbox_mensagem
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

create policy tenant_isolation on outbox_dlq
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

create function outbox_entrega_reclamar(p_limite int, p_bloqueado_ate timestamptz)
returns table (
  id uuid,
  ente_id uuid,
  chave text,
  destino text,
  tipo text,
  conteudo text,
  tentativas int
)
language sql
security definer
set search_path = pg_catalog, public
as $$
  with selecionadas as (
    select m.id
      from outbox_mensagem m
     where m.status in ('enfileirado', 'retentando')
       and m.proxima_tentativa_em <= clock_timestamp()
       and (m.bloqueado_ate is null or m.bloqueado_ate <= clock_timestamp())
     order by m.criado_em, m.id
     for update skip locked
     limit least(greatest(p_limite, 1), 1000)
  ),
  atualizadas as (
    update outbox_mensagem m
       set bloqueado_ate = p_bloqueado_ate,
           atualizado_em = clock_timestamp()
      from selecionadas s
     where m.id = s.id
     returning m.id, m.ente_id, m.chave, m.destino, m.tipo, m.conteudo, m.tentativas
  )
  select * from atualizadas;
$$;

create function outbox_entrega_confirmar(p_id uuid)
returns void
language sql
security definer
set search_path = pg_catalog, public
as $$
  update outbox_mensagem
     set status = 'entregue',
         bloqueado_ate = null,
         ultimo_erro = null,
         entregue_em = clock_timestamp(),
         atualizado_em = clock_timestamp()
   where id = p_id
     and status in ('enfileirado', 'retentando');
$$;

create function outbox_entrega_retentativa(p_id uuid, p_proxima_tentativa_em timestamptz, p_erro text)
returns void
language sql
security definer
set search_path = pg_catalog, public
as $$
  update outbox_mensagem
     set status = 'retentando',
         tentativas = tentativas + 1,
         proxima_tentativa_em = p_proxima_tentativa_em,
         bloqueado_ate = null,
         ultimo_erro = left(p_erro, 4000),
         atualizado_em = clock_timestamp()
   where id = p_id
     and status in ('enfileirado', 'retentando');
$$;

create function outbox_entrega_dlq(p_id uuid, p_erro text)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
  v_mensagem outbox_mensagem%rowtype;
begin
  update outbox_mensagem
     set status = 'falha_permanente',
         tentativas = tentativas + 1,
         bloqueado_ate = null,
         ultimo_erro = left(p_erro, 4000),
         atualizado_em = clock_timestamp()
   where id = p_id
     and status in ('enfileirado', 'retentando')
   returning * into v_mensagem;

  if found then
    insert into outbox_dlq (mensagem_id, ente_id, chave, destino, tipo, conteudo, tentativas, erro)
    values (
      v_mensagem.id,
      v_mensagem.ente_id,
      v_mensagem.chave,
      v_mensagem.destino,
      v_mensagem.tipo,
      v_mensagem.conteudo,
      v_mensagem.tentativas,
      left(p_erro, 4000)
    )
    on conflict (mensagem_id) do update
       set tentativas = excluded.tentativas,
           erro = excluded.erro,
           criado_em = clock_timestamp();
  end if;
end;
$$;

revoke all on outbox_mensagem from public;
revoke all on outbox_dlq from public;
revoke all on function
  outbox_entrega_reclamar(int, timestamptz),
  outbox_entrega_confirmar(uuid),
  outbox_entrega_retentativa(uuid, timestamptz, text),
  outbox_entrega_dlq(uuid, text)
from public;

grant select, insert, update on outbox_mensagem to app_role;
grant select on outbox_dlq to app_role;
grant execute on function
  outbox_entrega_reclamar(int, timestamptz),
  outbox_entrega_confirmar(uuid),
  outbox_entrega_retentativa(uuid, timestamptz, text),
  outbox_entrega_dlq(uuid, text)
to app_role;
