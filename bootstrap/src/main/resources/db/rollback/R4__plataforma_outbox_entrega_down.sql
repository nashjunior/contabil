-- Rollback manual de V4__plataforma_outbox_entrega.sql.
-- Flyway Community não roda "undo migrations"; ambiente efêmero/CI apenas.

drop function if exists outbox_entrega_dlq(uuid, text);
drop function if exists outbox_entrega_retentativa(uuid, timestamptz, text);
drop function if exists outbox_entrega_confirmar(uuid);
drop function if exists outbox_entrega_reclamar(int, timestamptz);
drop table if exists outbox_dlq;
drop table if exists outbox_mensagem;
