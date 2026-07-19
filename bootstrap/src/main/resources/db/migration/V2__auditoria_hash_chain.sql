-- Trilha de auditoria imutavel: append-only + hash-chain por ente — RAZ-6.
-- Ref.: ADR-0005, docs/transversais/04-lgpd.md, docs/13-nfr-e-operacao.md.
--
-- A aplicacao nao recebe INSERT/UPDATE/DELETE direto em auditoria_evento:
-- escreve apenas pela funcao append_auditoria_evento(), que deriva o ente da
-- mesma claim de sessao usada pela RLS (app.ente_id), usa timestamp do servidor
-- e calcula o hash no banco.

create extension if not exists pgcrypto;

create table auditoria_contador (
  ente_id            uuid primary key references ente(id),
  proxima_sequencia  bigint not null default 1,
  ultimo_hash        char(64),
  constraint ck_auditoria_contador_seq check (proxima_sequencia >= 1),
  constraint ck_auditoria_contador_hash check (ultimo_hash is null or ultimo_hash ~ '^[0-9a-f]{64}$')
);

insert into auditoria_contador (ente_id, proxima_sequencia)
select id, 1 from ente
on conflict (ente_id) do nothing;

create table auditoria_evento (
  id             uuid primary key default uuid_generate_v4(),
  ente_id        uuid not null references ente(id),
  sequencia      bigint not null,
  tipo           text not null,
  ator           text not null,
  recurso        text not null,
  momento        timestamptz not null,
  detalhes       jsonb not null default '{}'::jsonb,
  hash_anterior  char(64),
  hash_evento    char(64) not null,
  criado_em      timestamptz not null default clock_timestamp(),
  unique (ente_id, sequencia),
  unique (ente_id, hash_evento),
  constraint ck_auditoria_evento_seq check (sequencia >= 1),
  constraint ck_auditoria_evento_campos check (
    length(trim(tipo)) > 0 and length(trim(ator)) > 0 and length(trim(recurso)) > 0
  ),
  constraint ck_auditoria_evento_hashes check (
    hash_evento ~ '^[0-9a-f]{64}$'
    and (hash_anterior is null or hash_anterior ~ '^[0-9a-f]{64}$')
  ),
  constraint ck_auditoria_evento_primeiro_hash check (
    (sequencia = 1 and hash_anterior is null)
    or (sequencia > 1 and hash_anterior is not null)
  ),
  constraint ck_auditoria_evento_sem_cpf_claro check (
    not (
      ator ~ '[0-9]{3}[.]?[0-9]{3}[.]?[0-9]{3}-?[0-9]{2}'
      or recurso ~ '[0-9]{3}[.]?[0-9]{3}[.]?[0-9]{3}-?[0-9]{2}'
      or detalhes::text ~ '[0-9]{3}[.]?[0-9]{3}[.]?[0-9]{3}-?[0-9]{2}'
    )
  )
);

create index ix_auditoria_evento_ente_momento
  on auditoria_evento (ente_id, momento, sequencia);

create index ix_auditoria_evento_ente_tipo
  on auditoria_evento (ente_id, tipo, momento, sequencia);

alter table auditoria_contador enable row level security; alter table auditoria_contador force row level security;
alter table auditoria_evento   enable row level security; alter table auditoria_evento   force row level security;

create policy tenant_isolation on auditoria_contador
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

create policy tenant_isolation on auditoria_evento
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

create function inicializa_contador_auditoria() returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
  insert into auditoria_contador (ente_id, proxima_sequencia)
  values (new.id, 1)
  on conflict (ente_id) do nothing;
  return new;
end;
$$;

create trigger trg_inicializa_contador_auditoria
  after insert on ente
  for each row execute function inicializa_contador_auditoria();

create function bloqueia_mutacao_auditoria_evento() returns trigger as $$
begin
  raise exception 'auditoria_evento e append-only: update/delete proibido';
end;
$$ language plpgsql;

create trigger trg_imutavel_auditoria_evento
  before update or delete on auditoria_evento
  for each row execute function bloqueia_mutacao_auditoria_evento();

create function append_auditoria_evento(
  p_ente_id  uuid,
  p_tipo     text,
  p_ator     text,
  p_recurso  text,
  p_detalhes jsonb
) returns table (
  id             uuid,
  sequencia      bigint,
  momento        timestamptz,
  hash_evento    char(64),
  hash_anterior  char(64)
)
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
  v_ente_id       uuid := current_setting('app.ente_id', true)::uuid;
  v_id            uuid := uuid_generate_v4();
  v_sequencia     bigint;
  v_hash_anterior char(64);
  v_momento       timestamptz := clock_timestamp();
  v_hash_evento   char(64);
  v_detalhes      jsonb := coalesce(p_detalhes, '{}'::jsonb);
begin
  if p_ente_id is distinct from v_ente_id then
    raise exception 'ente do evento de auditoria diverge da sessao';
  end if;

  update auditoria_contador
     set proxima_sequencia = proxima_sequencia + 1
   where ente_id = v_ente_id
  returning proxima_sequencia - 1, ultimo_hash
       into v_sequencia, v_hash_anterior;

  if not found then
    raise exception 'Ente % sem contador de auditoria inicializado', v_ente_id;
  end if;

  v_hash_evento := encode(digest(concat_ws(E'\n',
      v_id::text,
      v_ente_id::text,
      v_sequencia::text,
      v_momento::text,
      p_tipo,
      p_ator,
      p_recurso,
      v_detalhes::text,
      coalesce(v_hash_anterior::text, '')
    ), 'sha256'), 'hex');

  insert into auditoria_evento (
      id, ente_id, sequencia, tipo, ator, recurso, momento, detalhes, hash_anterior, hash_evento
  ) values (
      v_id, v_ente_id, v_sequencia, p_tipo, p_ator, p_recurso, v_momento, v_detalhes, v_hash_anterior, v_hash_evento
  );

  update auditoria_contador
     set ultimo_hash = v_hash_evento
   where ente_id = v_ente_id;

  id := v_id;
  sequencia := v_sequencia;
  momento := v_momento;
  hash_evento := v_hash_evento;
  hash_anterior := v_hash_anterior;
  return next;
end;
$$;

revoke all on auditoria_contador, auditoria_evento from public;
revoke all on function append_auditoria_evento(uuid, text, text, text, jsonb) from public;
revoke all on function inicializa_contador_auditoria(), bloqueia_mutacao_auditoria_evento() from public;

grant select on auditoria_evento to app_role;
grant execute on function append_auditoria_evento(uuid, text, text, text, jsonb) to app_role;
