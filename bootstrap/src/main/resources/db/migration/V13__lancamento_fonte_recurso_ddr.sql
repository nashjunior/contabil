-- Fonte/destinação de recursos (FR) como dimensão do lançamento — RAZ-225.
-- Ref.: docs/arquitetura-tecnica/adr/0054-mecanica-postagem-ddr-e-fonte-recurso-no-lancamento.md
--       (ADR-0054), ADR-0050 (IC da MSC = dimensões do lançamento), ADR-0044 (trava art. 42 / DDR).
--
-- Migração ADITIVA e retrocompatível: coluna nullable, sem tocar as 4 travas do razão
-- (partidas dobradas, append-only, período, RLS) nem os grants — app_role segue sem
-- UPDATE/DELETE em lancamento (trava 2). A FR é CONDICIONAL por conta (obrigatória nas
-- contas de controle DDR classes 7/8 e nas orçamentárias que a alimentam); a regra
-- "conta X exige FR" é imposta na escrituração/origem, não como NOT NULL de coluna, para
-- não quebrar as partidas que legitimamente não carregam vinculação.
--
-- [REVALIDAR] o formato/comprimento exato do código de fonte contra o MCASP edição vigente
-- (mesma marcação de docs/15 e docs/17). varchar(20) é teto de segurança, não o formato final.

alter table lancamento
  add column fonte_recurso varchar(20);

comment on column lancamento.fonte_recurso is
  'Fonte/destinacao de recursos (vinculacao) — IC FR da MSC / chave da DDR e do gate art.42 (ADR-0054). Nullable: dimensao condicional por conta. [REVALIDAR] formato no MCASP vigente.';

-- Serve a apuracao por fonte (DisponibilidadePorFonte: GROUP BY conta x fonte_recurso, sem
-- compensacao entre fontes). Parcial: so as partidas vinculadas entram no indice.
create index ix_lancamento_fonte_recurso
  on lancamento (ente_id, fonte_recurso, conta_id)
  where fonte_recurso is not null;
