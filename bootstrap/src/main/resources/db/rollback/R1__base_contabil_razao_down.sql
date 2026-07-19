-- Rollback manual de V1__base_contabil_razao.sql — RAZ-3.
--
-- Flyway Community não roda "undo migrations" (recurso pago do Teams/Enterprise);
-- por isso este script fica FORA de db/migration (não é lido pelo Flyway) e é
-- aplicado manualmente (psql/CI) quando necessário. Uso pretendido: ambiente
-- efêmero/CI ("guardrail" de reversibilidade citado em
-- docs/arquitetura-tecnica/README.md §8). NÃO rodar em produção: uma vez que
-- o razão tem fatos consolidados, "desfazer" o schema apaga a fonte da
-- verdade — a única correção legítima em produção é estorno, nunca rollback
-- de schema.

drop view if exists saldo_conta;

drop function if exists proximo_numero_seq();
drop trigger if exists trg_inicializa_contador_fato on ente;
drop function if exists inicializa_contador_fato();

drop trigger if exists trg_anti_backdating on fato_contabil;
drop function if exists forca_data_hora_registro();

drop trigger if exists trg_periodo_aberto on fato_contabil;
drop function if exists checa_periodo_aberto();

drop trigger if exists trg_imutavel_fato on fato_contabil;
drop trigger if exists trg_imutavel_lancamento on lancamento;
drop function if exists bloqueia_mutacao();

drop trigger if exists trg_partidas_dobradas on lancamento;
drop function if exists checa_partidas_dobradas();

drop table if exists lancamento;
drop table if exists fato_contabil;
drop table if exists contador_fato;
drop table if exists periodo_contabil;
drop table if exists conta_pcasp;
drop table if exists ente;

-- app_role fica (pode ser reusado pela próxima aplicação da V1); dropar só
-- em ambiente efêmero totalmente descartado:
-- drop role if exists app_role;
