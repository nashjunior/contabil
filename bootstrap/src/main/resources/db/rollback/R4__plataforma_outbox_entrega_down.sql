-- Rollback manual de V4__plataforma_outbox_entrega.sql.
-- Flyway Community não roda "undo migrations"; ambiente efêmero/CI apenas.

drop table if exists outbox_mensagem;
