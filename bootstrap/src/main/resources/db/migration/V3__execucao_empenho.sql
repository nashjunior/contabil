-- Execução orçamentária: dotação + empenho — RAZ-66.
-- Ref.: docs/arquitetura-tecnica/execucao-orcamentaria-despesa.md, ADR-0021
-- (contabilização da execução da despesa), razao-contabil-schema.md (padrão
-- de travas reaproveitado aqui: RLS forçada, FK composta com ente_id).
--
-- Saldo da dotação é DERIVADO (soma dos empenhos vinculados), nunca uma coluna
-- mutada — dotacao.valor_autorizado só muda por carga da LOA/créditos
-- adicionais (issue própria, fora do escopo de RAZ-66). A trava de concorrência
-- (empenho <= crédito, art. 59) vem do `select ... for update` na linha da
-- dotação (PostgresSaldosExecucaoPort), que serializa duas transações
-- concorrentes sobre a mesma dotação.

-- 1. Dotação (vínculo orçamentário — raiz do saldo operacional).
create table dotacao (
  id                          uuid not null default uuid_generate_v4(),
  ente_id                     uuid not null references ente(id),
  exercicio                   int not null,
  classificacao_orcamentaria  text not null,
  fonte_recurso               text not null,
  unidade_gestora_id          uuid not null,
  valor_autorizado            numeric(18,2) not null check (valor_autorizado >= 0),
  primary key (id),
  unique (ente_id, id)                                  -- ancora a FK composta de empenho.dotacao_id
);

-- 2. Contador do empenho — numeração sequencial gapless por ente/exercício
--    (mesmo padrão do contador_fato do razão, mas com o exercício na chave:
--    a numeração do empenho reinicia a cada exercício). Sem grant direto a
--    app_role: só via proximo_numero_empenho() (ver abaixo).
create table contador_empenho (
  ente_id    uuid not null references ente(id),
  exercicio  int not null,
  proximo    bigint not null default 1,
  primary key (ente_id, exercicio)
);

-- 3. Empenho (Lei 4.320/1964 art. 58-60). F1 cobre só o registro inicial —
--    sem estado/ciclo de vida (reforço/anulação são issues próprias).
create table empenho (
  id                          uuid not null default uuid_generate_v4(),
  ente_id                     uuid not null references ente(id),
  numero_sequencial           bigint not null,
  exercicio                   int not null,
  tipo                        text not null check (tipo in ('ordinario','estimativo','global')),
  dotacao_id                  uuid not null,
  credor_id                   uuid not null,             -- Pessoa (cadastro/plataforma) — sem FK: fora do escopo de RAZ-66
  unidade_gestora_id          uuid not null,              -- sem FK: unidade_gestora ainda não é tabela própria
  contrato_id                 uuid,                       -- opcional (10-modelo-dados.md: CONTRATO ||--o{ EMPENHO)
  valor                       numeric(18,2) not null check (valor > 0),  -- dinheiro em DECIMAL, nunca float (ADR-0006)
  data_fato                   date not null,
  classificacao_orcamentaria  text not null,
  fonte_recurso               text not null,
  historico                   text not null,
  fato_contabil_id            uuid not null,              -- vínculo 1:1 evento->fato (ADR-0021)
  criado_em                   timestamptz not null default clock_timestamp(),
  primary key (id),
  unique (ente_id, id),
  unique (ente_id, exercicio, numero_sequencial),
  foreign key (ente_id, dotacao_id) references dotacao (ente_id, id),
  foreign key (ente_id, fato_contabil_id) references fato_contabil (ente_id, id)
);
create index ix_empenho_ente_dotacao on empenho (ente_id, dotacao_id);

-- ============================================================================
-- RLS forçada (mesmo padrão da trava 4 do razão) — deny-by-default.
-- ============================================================================
alter table dotacao          enable row level security; alter table dotacao          force row level security;
alter table contador_empenho enable row level security; alter table contador_empenho force row level security;
alter table empenho          enable row level security; alter table empenho          force row level security;

create policy tenant_isolation on dotacao
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

create policy tenant_isolation on contador_empenho
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

create policy tenant_isolation on empenho
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

-- ============================================================================
-- Numeração sequencial gapless do empenho por ente/exercício — SECURITY
-- DEFINER porque app_role não tem acesso direto a contador_empenho (só via
-- esta função). Inicializa a linha do (ente, exercício) sob demanda: ao
-- contrário do contador_fato (uma linha por ente, criada no insert de ente),
-- o exercício só existe quando o primeiro empenho daquele ano é registrado.
--
-- SEM parâmetro de ente (mesma razão do proximo_numero_seq() do razão,
-- RAZ-14): deriva de current_setting('app.ente_id'), nunca de argumento do
-- chamador — evita que um app_login do ente A numere para o ente B.
-- ============================================================================
create function proximo_numero_empenho(p_exercicio int) returns bigint
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
  v_ente_id uuid := current_setting('app.ente_id', true)::uuid;
  v_numero  bigint;
begin
  insert into contador_empenho (ente_id, exercicio, proximo)
  values (v_ente_id, p_exercicio, 1)
  on conflict (ente_id, exercicio) do nothing;

  update contador_empenho
     set proximo = proximo + 1
   where ente_id = v_ente_id and exercicio = p_exercicio
  returning proximo - 1 into v_numero;

  if not found then
    raise exception 'Ente % sem contador de empenho para o exercicio %', v_ente_id, p_exercicio;
  end if;

  return v_numero;
end;
$$;

-- ============================================================================
-- Grants — least-privilege. app_role NUNCA recebe DELETE.
--
-- dotacao recebe UPDATE mesmo sem nenhum UPDATE de aplicação hoje: o Postgres
-- exige o privilégio UPDATE (além de SELECT) para `select ... for update`
-- (PostgresSaldosExecucaoPort trava a linha da dotação para serializar
-- empenhos concorrentes) — confirmado rodando a migration num Postgres real
-- (docker) e reproduzindo o "permission denied" sem este grant. empenho: sem
-- UPDATE por ora (o ciclo de vida do empenho — reforço/anulação — é issue
-- própria que revisita este grant quando implementar a mutação real).
-- ============================================================================
revoke all on dotacao, contador_empenho, empenho from public;
revoke all on function proximo_numero_empenho(int) from public;

grant select, insert, update on dotacao          to app_role;
grant select, insert        on empenho           to app_role;
grant execute                on function proximo_numero_empenho(int) to app_role;
