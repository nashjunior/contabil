-- Rollback manual de V3__execucao_empenho.sql — RAZ-66.
--
-- Flyway Community não roda "undo migrations"; este script fica FORA de
-- db/migration e é aplicado manualmente (psql/CI) quando necessário. Uso
-- pretendido: ambiente efêmero/CI. NÃO rodar em produção com empenhos
-- consolidados: a correção legítima é reforço/anulação/estorno, nunca
-- rollback de schema.

drop function if exists proximo_numero_empenho(int);

drop table if exists empenho;
drop table if exists contador_empenho;
drop table if exists dotacao;
