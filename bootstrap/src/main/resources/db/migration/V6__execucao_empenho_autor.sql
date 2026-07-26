-- ADR-0023 (gate de aprovação do pagamento): a checagem anti-auto-aprovação de
-- AprovarPagamento (RAZ-92) precisa comparar o aprovador contra a identidade
-- de quem registrou o empenho da cadeia (Regra 9 — "o ordenador/autorizador
-- não pode ser o autor do lançamento"). empenho já é tabela live (V3); a
-- coluna chega por ALTER, não recriação.
--
-- Nullable (sem NOT NULL/backfill): empenho já pode ter linhas anteriores a
-- esta migration, e não há identidade real para forjar num backfill (Regra 7
-- veda usuário genérico — inventar um autor_cpf placeholder seria pior que
-- deixar a coluna nula). O domínio (Empenho.registrar) já exige autor
-- non-null para toda escrita nova pelo código; só linhas legadas ficam nulas.

alter table empenho add column autor_cpf text;
