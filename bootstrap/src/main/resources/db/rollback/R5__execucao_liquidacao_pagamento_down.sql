-- Rollback manual de V5__execucao_liquidacao_pagamento.sql — RAZ-67/RAZ-96.
--
-- Flyway Community não roda "undo migrations"; este script fica FORA de
-- db/migration e é aplicado manualmente (psql/CI) quando necessário. Uso
-- pretendido: ambiente efêmero/CI (reversibilidade de migração,
-- arquitetura-tecnica §8 — provado por ReversibilidadeMigracaoFlywayTest).
-- NÃO rodar em produção com liquidações/pagamentos consolidados: a correção
-- legítima é anulação/estorno por novo movimento, nunca rollback de schema.

-- Reverte a única alteração de V5 sobre objeto de OUTRA migração: o UPDATE que
-- V5 concedeu em empenho (para o `select ... for update` do adapter de saldos).
-- Restaura empenho ao conjunto de grants da V3 (select/insert). Roda antes de
-- R3 (que dropa empenho), então a tabela ainda existe aqui.
revoke update on empenho from app_role;

-- pagamento referencia liquidacao (FK composta ente_id+liquidacao_id): cai antes.
-- Índices, políticas RLS e grants próprios somem junto com as tabelas.
drop table if exists pagamento;
drop table if exists liquidacao;
