-- Rollback de V15 — restaura o dominio original (estreito) do check
-- fato_contabil_tipo_evento_check, sem os 4 tipos de encerramento/RP
-- (paridade V<->R, RAZ-72). Apenas ambiente efemero/CI.

alter table fato_contabil
  drop constraint if exists fato_contabil_tipo_evento_check;

alter table fato_contabil
  add constraint fato_contabil_tipo_evento_check
  check (tipo_evento in ('empenho', 'liquidacao', 'pagamento', 'receita', 'estorno', 'abertura'));
