-- Rollback manual de V2__auditoria_hash_chain.sql — RAZ-6.
--
-- Apenas para ambiente efemero/CI. Em producao, a trilha e evidencia de
-- auditoria: nao deve ser apagada como mecanismo ordinario de correcao.

drop function if exists append_auditoria_evento(uuid, text, text, text, jsonb);

drop trigger if exists trg_imutavel_auditoria_evento on auditoria_evento;
drop function if exists bloqueia_mutacao_auditoria_evento();

drop trigger if exists trg_inicializa_contador_auditoria on ente;
drop function if exists inicializa_contador_auditoria();

drop table if exists auditoria_evento;
drop table if exists auditoria_contador;
