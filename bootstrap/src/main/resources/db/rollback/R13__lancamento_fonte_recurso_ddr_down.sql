-- Rollback manual de V13__lancamento_fonte_recurso_ddr.sql — RAZ-225.
-- Ambiente efêmero/CI apenas: remove a dimensão fonte de recursos (FR) do lançamento.

drop index if exists ix_lancamento_fonte_recurso;

alter table lancamento
  drop column if exists fonte_recurso;
