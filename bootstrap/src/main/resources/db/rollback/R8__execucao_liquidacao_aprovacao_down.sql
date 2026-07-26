-- Rollback manual de V7__execucao_liquidacao_aprovacao.sql — RAZ-105.
--
-- Flyway Community não roda "undo migrations"; este script fica FORA de
-- db/migration e é aplicado manualmente (psql/CI) quando necessário. Uso
-- pretendido: ambiente efêmero/CI.

alter table liquidacao
  drop column if exists autor_cpf,
  drop column if exists motivo_devolucao,
  drop column if exists aprovador_cpf,
  drop column if exists status_aprovacao;
