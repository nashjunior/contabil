-- infra/restore/verificacoes.sql — verificações de integridade da base RESTAURADA (F0).
--
-- Rode contra a instância scratch, como superusuário ou papel BYPASSRLS
-- (auditoria_evento tem `force row level security`; sem bypass a leitura vem vazia).
-- Cada linha do resultado é uma verificação: resultado = OK | FALHA.
-- O restore-drill reprova a restauração se qualquer linha sair 'FALHA'.
--
-- Ancorado no schema REAL do repo (migrações V1+V2): tudo em `public` — nenhuma
-- migração cria schema `contabil`. Tabelas `flyway_schema_history`, `ente`,
-- `auditoria_evento`; a chave de isolamento é a coluna `ente_id` (ADR-0015).
\set ON_ERROR_STOP on
\pset pager off

with
flyway as (
    select count(*)                                as total,
           count(*) filter (where success = false) as falhas
    from flyway_schema_history
),
aud_gaps as (   -- entes cuja sequência não é contígua a partir de 1
    select count(*) as n from (
        select ente_id
        from auditoria_evento
        group by ente_id
        having min(sequencia) <> 1 or max(sequencia) <> count(*)
    ) g
),
aud_chain as (  -- elos da hash-chain quebrados (hash_anterior <> hash do anterior)
    select count(*) as n
    from auditoria_evento e
    left join auditoria_evento p
      on p.ente_id = e.ente_id and p.sequencia = e.sequencia - 1
    where e.sequencia > 1
      and (p.hash_evento is null or e.hash_anterior is distinct from p.hash_evento)
),
aud_first as (  -- primeiro evento de cada cadeia deve ter hash_anterior nulo
    select count(*) as n
    from auditoria_evento
    where sequencia = 1 and hash_anterior is not null
),
aud_orfao as (  -- evento sem ente correspondente (restauração parcial)
    select count(*) as n
    from auditoria_evento a
    left join ente t on t.id = a.ente_id
    where t.id is null
)
select resultado, verificacao, detalhe
from (
    select 1 as ord,
        case when (select falhas from flyway) = 0 and (select total from flyway) >= 1
             then 'OK' else 'FALHA' end as resultado,
        'flyway_migracoes' as verificacao,
        (select total from flyway)::text || ' migração(ões), '
          || (select falhas from flyway)::text || ' com falha' as detalhe
    union all
    select 2,
        case when (select n from aud_gaps) = 0 then 'OK' else 'FALHA' end,
        'auditoria_sequencia_contigua',
        (select n from aud_gaps)::text || ' ente(s) com lacuna de sequência'
    union all
    select 3,
        case when (select n from aud_chain) = 0 then 'OK' else 'FALHA' end,
        'auditoria_hash_encadeado',
        (select n from aud_chain)::text || ' elo(s) de hash quebrado(s)'
    union all
    select 4,
        case when (select n from aud_first) = 0 then 'OK' else 'FALHA' end,
        'auditoria_primeiro_hash_nulo',
        (select n from aud_first)::text || ' evento(s) inicial(is) com hash_anterior não nulo'
    union all
    select 5,
        case when (select n from aud_orfao) = 0 then 'OK' else 'FALHA' end,
        'auditoria_sem_orfao_ente',
        (select n from aud_orfao)::text || ' evento(s) órfão(s)'
) t
order by ord;

-- Nota (RAZ-50): a verificação de órfãos do outbox foi removida — nenhuma migração
-- cria `publicacao_outbox` no schema real; referenciá-la quebrava o oráculo na mesma
-- classe de bug corrigida aqui. Reintroduzir, gated por to_regclass, quando o outbox
-- transacional entrar (análogo ao gancho do razão em verificacoes-razao.sql).
