-- Execução orçamentária: liquidação + pagamento — completa a base operacional
-- que RAZ-67 já modelou nos use cases e que PostgresSaldosExecucaoPort consulta.
--
-- Os saldos seguem derivados: liquidação soma pagamentos, empenho soma
-- liquidações. A trava de concorrência fica na linha do estágio anterior via
-- `select ... for update` no adapter de saldos.

create table liquidacao (
  id                    uuid not null default uuid_generate_v4(),
  ente_id               uuid not null references ente(id),
  empenho_id            uuid not null,
  data_competencia      date not null,
  valor                 numeric(18,2) not null check (valor > 0),
  documentos_suporte    jsonb not null default '[]'::jsonb,
  historico             text not null,
  fato_contabil_id      uuid not null,
  criado_em             timestamptz not null default clock_timestamp(),
  primary key (id),
  unique (ente_id, id),
  foreign key (ente_id, empenho_id) references empenho (ente_id, id),
  foreign key (ente_id, fato_contabil_id) references fato_contabil (ente_id, id)
);
create index ix_liquidacao_ente_empenho on liquidacao (ente_id, empenho_id);

create table pagamento (
  id                       uuid not null default uuid_generate_v4(),
  ente_id                  uuid not null references ente(id),
  liquidacao_id            uuid not null,
  data_competencia         date not null,
  valor                    numeric(18,2) not null check (valor > 0),
  natureza                 text not null check (natureza in ('orcamentario','folha_consolidada')),
  beneficiario_nome        text,
  beneficiario_cpf_cnpj    text,
  ordem_bancaria           text,
  historico                text not null,
  fato_contabil_id         uuid not null,
  criado_em                timestamptz not null default clock_timestamp(),
  primary key (id),
  unique (ente_id, id),
  foreign key (ente_id, liquidacao_id) references liquidacao (ente_id, id),
  foreign key (ente_id, fato_contabil_id) references fato_contabil (ente_id, id),
  check (natureza = 'folha_consolidada' or (beneficiario_nome is not null and beneficiario_cpf_cnpj is not null))
);
create index ix_pagamento_ente_liquidacao on pagamento (ente_id, liquidacao_id);

alter table liquidacao enable row level security; alter table liquidacao force row level security;
alter table pagamento   enable row level security; alter table pagamento   force row level security;

create policy tenant_isolation on liquidacao
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

create policy tenant_isolation on pagamento
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

-- app_role precisa de UPDATE nas linhas travadas por `select ... for update`.
-- Não há UPDATE de negócio exposto; correções continuam por movimentos próprios.
revoke all on liquidacao, pagamento from public;
grant update on empenho to app_role;
grant select, insert, update on liquidacao to app_role;
grant select, insert         on pagamento   to app_role;
