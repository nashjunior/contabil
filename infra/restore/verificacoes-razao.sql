-- infra/restore/verificacoes-razao.sql — GANCHO da invariante do razão (Σd=Σc).
--
-- O restore-drill SÓ executa este arquivo quando a tabela do razão já existe na
-- base restaurada (probe via to_regclass em `lancamento`). Hoje a migração do razão
-- (V1) já cria o núcleo, então este gancho é ATIVO e não mais inerte.
--
-- Schema real (V1, ver docs/arquitetura-tecnica/razao-contabil-schema.md §Trava 1):
--   lancamento(fato_id uuid, natureza char(1) in ('D','C'), valor numeric(18,2))
--     -- append-only; dinheiro em DECIMAL (ADR-0006); partidas dobradas POR FATO.
-- O "lançamento" contábil (a entrada balanceada) é o `fato_contabil`; suas partidas
-- de débito/crédito são as linhas de `lancamento` agrupadas por `fato_id`.
\set ON_ERROR_STOP on
\pset pager off

with
por_fato as (   -- Σdébito = Σcrédito em CADA fato contábil (partidas dobradas)
    select count(*) as n_desbalanceados from (
        select fato_id
        from lancamento
        group by fato_id
        having coalesce(sum(valor) filter (where natureza = 'D'), 0)
             <> coalesce(sum(valor) filter (where natureza = 'C'), 0)
    ) d
),
balanco as (          -- Σdébito = Σcrédito GLOBAL
    select coalesce(sum(valor) filter (where natureza = 'D'), 0) as deb,
           coalesce(sum(valor) filter (where natureza = 'C'), 0) as cred
    from lancamento
)
select resultado, verificacao, detalhe
from (
    select 1 as ord,
        case when (select n_desbalanceados from por_fato) = 0
             then 'OK' else 'FALHA' end as resultado,
        'razao_partidas_dobradas' as verificacao,
        (select n_desbalanceados from por_fato)::text
          || ' fato(s) com Σdébito<>Σcrédito' as detalhe
    union all
    select 2,
        case when (select deb from balanco) = (select cred from balanco)
             then 'OK' else 'FALHA' end,
        'razao_balanco_global',
        'Σdébito=' || (select deb from balanco)::text
          || ' Σcrédito=' || (select cred from balanco)::text
) t
order by ord;
