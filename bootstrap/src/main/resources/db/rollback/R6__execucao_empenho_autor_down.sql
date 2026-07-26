-- Rollback manual de V6__execucao_empenho_autor.sql — RAZ-92.
--
-- Flyway Community não roda "undo migrations"; este script fica FORA de
-- db/migration e é aplicado manualmente (psql/CI) quando necessário. Uso
-- pretendido: ambiente efêmero/CI.

alter table empenho drop column if exists autor_cpf;
