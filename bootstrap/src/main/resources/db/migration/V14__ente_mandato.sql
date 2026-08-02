-- Janela de mandato do ente — RAZ-243 (ADR-0044: "janela de mandato precisa ser
-- parametrizável (datas de início/fim do mandato por ente) — dado de configuração
-- do ente, não hard-coded"), base do gate art. 42 (VerificarDisponibilidadeArt42).
--
-- Migração ADITIVA e retrocompatível: colunas nullable em `ente` — ente sem mandato
-- configurado simplesmente não tem o gate art. 42 avaliado (monitor/gate não bloqueia
-- sem dado, mesmo racional do restante do roteiro DDR condicional, ADR-0054).
--
-- Escopo: só o mandato VIGENTE (1 por ente). Histórico multi-mandato fica fora
-- (F2+, se algum consumidor futuro precisar) — corte de escopo análogo ao de RAZ-225.

alter table ente
  add column mandato_inicio date,
  add column mandato_fim    date,
  add constraint ck_ente_mandato_datas
    check (mandato_inicio is null or mandato_fim is null or mandato_fim > mandato_inicio);

comment on column ente.mandato_inicio is
  'Início do mandato vigente do titular do Poder/órgão — config do ente (ADR-0044). Nullable: gate art.42 não bloqueia sem config.';
comment on column ente.mandato_fim is
  'Fim do mandato vigente — a janela do art.42 (2 últimos quadrimestres) é maio-dezembro do ano desta data.';
