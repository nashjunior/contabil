-- infra/restore/verificacoes-outbox.sql — GANCHO da integridade do outbox de entrega (RAZ-9/RAZ-70).
--
-- O restore-drill SÓ executa este arquivo quando a tabela do outbox já existe na
-- base restaurada (probe via to_regclass em `outbox_mensagem`). A migração V4
-- já cria o outbox transacional, então este gancho é ATIVO e não mais inerte
-- (nota histórica: RAZ-50 removeu a checagem anterior porque nenhuma migração
-- criava `publicacao_outbox` na época; RAZ-70 entregou o outbox real com nomes
-- de tabela diferentes — `outbox_mensagem`/`outbox_dlq` — e este arquivo reintroduz
-- a verificação, análogo ao gancho do razão em verificacoes-razao.sql).
--
-- Rode contra a instância scratch, como superusuário ou papel BYPASSRLS
-- (outbox_mensagem/outbox_dlq têm `force row level security`; sem bypass a
-- leitura vem vazia e as checagens de órfão dariam falso-negativo).
\set ON_ERROR_STOP on
\pset pager off

with
outbox_orfao as (   -- outbox_mensagem sem ente correspondente (restauração parcial)
    select count(*) as n
    from outbox_mensagem m
    left join ente t on t.id = m.ente_id
    where t.id is null
),
dlq_orfao as (   -- outbox_dlq sem outbox_mensagem correspondente
    select count(*) as n
    from outbox_dlq d
    left join outbox_mensagem m on m.ente_id = d.ente_id and m.id = d.mensagem_id
    where m.id is null
),
rls as (   -- RLS deny-by-default (RAZ-85) precisa sobreviver à restauração
    select count(*) as n
    from pg_tables
    where schemaname = 'public'
      and tablename in ('outbox_mensagem', 'outbox_dlq')
      and (not rowsecurity or not (
            select relforcerowsecurity from pg_class c where c.relname = pg_tables.tablename
          ))
)
select resultado, verificacao, detalhe
from (
    select 1 as ord,
        case when (select n from outbox_orfao) = 0 then 'OK' else 'FALHA' end as resultado,
        'outbox_sem_orfao_ente' as verificacao,
        (select n from outbox_orfao)::text || ' mensagem(ns) órfã(s) de outbox_mensagem' as detalhe
    union all
    select 2,
        case when (select n from dlq_orfao) = 0 then 'OK' else 'FALHA' end,
        'outbox_dlq_sem_orfao_mensagem',
        (select n from dlq_orfao)::text || ' registro(s) órfão(s) de outbox_dlq'
    union all
    select 3,
        case when (select n from rls) = 0 then 'OK' else 'FALHA' end,
        'outbox_rls_forcada',
        (select n from rls)::text || ' tabela(s) do outbox sem RLS enable+force'
) t
order by ord;
