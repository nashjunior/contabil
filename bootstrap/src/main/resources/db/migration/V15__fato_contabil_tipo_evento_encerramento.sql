-- Amplia o dominio de fato_contabil.tipo_evento (trava V1) para os tipos ja emitidos em
-- producao pelo rito de encerramento e ainda ausentes na constraint original, que so
-- previa ('empenho','liquidacao','pagamento','receita','estorno','abertura'):
--
--   - 'encerramento'                  -- EncerrarContasOrcamentarias (RAZ-258)
--   - 'inscricao_restos_a_pagar'      -- InscreverRestosAPagar (RAZ-207)
--   - 'cancelamento_restos_a_pagar'   -- CancelarRestoAPagar (RAZ-207)
--   - 'encerramento_ddr'              -- EncerrarDdrPorFonte (RAZ-244)
--
-- Sem a correcao, qualquer fato desses tipos viola o check no INSERT (gap so nao
-- detectado ate agora por falta de teste de integracao contra Postgres real para esses
-- use cases — cobertos hoje só por teste unitario com repositorio mockado).

alter table fato_contabil
  drop constraint fato_contabil_tipo_evento_check;

alter table fato_contabil
  add constraint fato_contabil_tipo_evento_check
  check (tipo_evento in (
    'empenho', 'liquidacao', 'pagamento', 'receita', 'estorno', 'abertura',
    'encerramento', 'inscricao_restos_a_pagar', 'cancelamento_restos_a_pagar',
    'encerramento_ddr'
  ));
