-- Rollback manual de V7__execucao_empenho_assinatura.sql — RAZ-103.
-- Flyway Community não roda "undo migrations"; este script fica FORA de
-- db/migration e é aplicado manualmente (psql/CI) quando necessário. Uso
-- pretendido: ambiente efêmero/CI. NÃO rodar em produção com notas de
-- empenho já assinadas: a correção legítima segue estorno + novo documento,
-- nunca rollback de schema.

drop function if exists execucao_empenho_documento_outbox_dlq(uuid, text);
drop function if exists execucao_empenho_documento_outbox_retentativa(uuid, timestamptz, text);
drop function if exists execucao_empenho_documento_outbox_confirmar(uuid);
drop function if exists execucao_empenho_documento_outbox_reclamar(int, timestamptz);
drop table if exists execucao_empenho_documento_outbox;
drop table if exists execucao_empenho_documento_assinado;

alter table empenho drop column if exists documento_pendente_uri;
alter table empenho drop column if exists status;
