drop table if exists transparencia_publicacao cascade;
drop function if exists transparencia_publicacao_inserir(uuid, text, text, timestamptz, timestamptz, jsonb, text);
drop function if exists bloqueia_mutacao_transparencia_publicacao();
revoke usage on schema public from transparencia_publico_role;
drop role if exists transparencia_publico_role;
