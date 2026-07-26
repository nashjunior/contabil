-- RAZ-105 (fecha o gap deixado por RAZ-92/ADR-0023): AprovarPagamento e o
-- agregado Liquidacao (Liquidacao.aprovar/devolver) já modelam status_aprovacao/
-- aprovador_cpf/motivo_devolucao em código, mas a tabela liquidacao (V5) nunca
-- ganhou essas colunas — LiquidacaoRepository.atualizarDecisaoAprovacao não
-- tinha onde persistir. liquidacao já é tabela live (V5); a coluna chega por
-- ALTER, não recriação.
--
-- status_aprovacao NOT NULL com default 'pendente' PERMANENTE (ao contrário de
-- autor_cpf/V6, nullable sem backfill): toda Liquidacao.registrar(...) já nasce
-- com StatusAprovacao.PENDENTE — não existe linha "sem decisão" que não seja
-- pendente, então o default cobre o legado sem inventar estado. Mantido (não
-- dropado) de propósito: fixtures de teste existentes (ex.:
-- ExecucaoSaldosEstagiosIntegrationTest) inserem liquidacao via SQL cru sem
-- mencionar esta coluna nova; a aplicação sempre grava o valor explicitamente
-- (LiquidacaoRepository), então o default nunca mascara um bug de escrita real
-- — só evita quebrar todo INSERT cru pré-existente que ainda não conhece a
-- coluna. aprovador_cpf/motivo_devolucao ficam nulos até a decisão (Liquidacao
-- só os preenche em aprovar/devolver).
alter table liquidacao
  add column status_aprovacao text not null default 'pendente'
    check (status_aprovacao in ('pendente', 'aprovada', 'devolvida')),
  add column aprovador_cpf   text,
  add column motivo_devolucao text;

-- autor_cpf também nunca chegou à tabela (só o empenho ganhou em V6): o gate
-- de auto-aprovação (AprovarPagamento/Liquidacao#aprovar) compara o aprovador
-- contra o autor da PRÓPRIA liquidação, não só o do empenho da cadeia — sem
-- esta coluna, LiquidacaoRepository não tem como reidratar Liquidacao.autor(),
-- que o domínio exige non-null (Liquidacao.registrar). Nullable pela mesma
-- razão de V6: sem identidade real para forjar num backfill de linhas legadas.
alter table liquidacao add column autor_cpf text;
