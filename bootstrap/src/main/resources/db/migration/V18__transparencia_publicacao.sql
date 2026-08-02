-- Read model público append-only da transparência ativa — RAZ-273 / ADR-0059.
-- Única fonte de leitura do portal/API de dados abertos: payload já público,
-- minimizado e mascarado. O razão/OLTP não é consultado pela borda pública.

do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'transparencia_publico_role') then
    create role transparencia_publico_role nologin;
  end if;
end;
$$;

alter role transparencia_publico_role nologin nosuperuser nocreatedb nocreaterole noreplication;

create table transparencia_publicacao (
  ente_id              uuid not null references ente(id),
  tipo_evento          text not null,
  recurso              text not null,
  sequencia            bigint generated always as identity,
  publicado_em         timestamptz not null default clock_timestamp(),
  publicar_ate         timestamptz not null,
  payload_json         jsonb not null,
  chave_idempotencia   text not null,
  primary key (ente_id, sequencia),
  unique (chave_idempotencia),
  unique (ente_id, recurso, sequencia),
  check (publicado_em <= publicar_ate),
  check (jsonb_typeof(payload_json) = 'object'),
  check (payload_json ? 'estagio'),
  check (payload_json->>'estagio' in ('empenhado', 'liquidado', 'pago', 'receita', 'desconhecido'))
);

create index ix_transparencia_publicacao_ordem
  on transparencia_publicacao (ente_id, publicado_em desc, sequencia desc);

create index ix_transparencia_publicacao_credor
  on transparencia_publicacao (ente_id, (payload_json->>'credorId'));

create index ix_transparencia_publicacao_orgao
  on transparencia_publicacao (ente_id, (coalesce(payload_json->>'orgaoId', payload_json->>'unidadeGestoraId')));

create index ix_transparencia_publicacao_periodo
  on transparencia_publicacao (ente_id, (coalesce(payload_json->>'dataFato', payload_json->>'dataCompetencia')));

create index ix_transparencia_publicacao_funcao
  on transparencia_publicacao (ente_id, (payload_json->>'funcao'));

create index ix_transparencia_publicacao_numero_empenho
  on transparencia_publicacao (ente_id, (((payload_json->>'numeroSequencial')::bigint)));

create index ix_transparencia_publicacao_contrato
  on transparencia_publicacao (ente_id, (payload_json->>'contratoId'));

create function bloqueia_mutacao_transparencia_publicacao() returns trigger as $$
begin
  raise exception 'Read model publico append-only. Publique nova versão do recurso em nova linha.';
end;
$$ language plpgsql;

create trigger trg_transparencia_publicacao_append_only
  before update or delete on transparencia_publicacao
  for each row execute function bloqueia_mutacao_transparencia_publicacao();

alter table transparencia_publicacao enable row level security;
alter table transparencia_publicacao force row level security;

create policy tenant_isolation on transparencia_publicacao
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

create function transparencia_publicacao_inserir(
  p_ente_id uuid,
  p_tipo_evento text,
  p_recurso text,
  p_publicado_em timestamptz,
  p_publicar_ate timestamptz,
  p_payload_json jsonb,
  p_chave_idempotencia text
) returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
  perform set_config('app.ente_id', p_ente_id::text, true);

  insert into transparencia_publicacao
      (ente_id, tipo_evento, recurso, publicado_em, publicar_ate, payload_json, chave_idempotencia)
  values
      (p_ente_id, p_tipo_evento, p_recurso, p_publicado_em, p_publicar_ate, p_payload_json, p_chave_idempotencia)
  on conflict (chave_idempotencia) do nothing;
end;
$$;

revoke all on transparencia_publicacao from public;
revoke all on function bloqueia_mutacao_transparencia_publicacao() from public;
revoke all on function transparencia_publicacao_inserir(uuid, text, text, timestamptz, timestamptz, jsonb, text) from public;

grant usage on schema public to transparencia_publico_role;
grant select on transparencia_publicacao to transparencia_publico_role;

grant select, insert on transparencia_publicacao to app_role;
grant usage, select on sequence transparencia_publicacao_sequencia_seq to app_role;
grant execute on function transparencia_publicacao_inserir(uuid, text, text, timestamptz, timestamptz, jsonb, text) to app_role;
