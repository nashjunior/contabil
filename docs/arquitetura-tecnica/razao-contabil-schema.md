# Schema do razão contábil (PostgreSQL)

[← Arquitetura técnica](./README.md) · [Modelo de dados](../10-modelo-dados.md) · [Motor de partidas dobradas (domínio)](./motor-razao-partidas-dobradas.md)

> Implementação (DDL) do **núcleo contábil** — a base única fonte da verdade. Materializa o conceito "Razão contábil" do [modelo de dados](../10-modelo-dados.md#razão-contábil-núcleo) com as **travas impostas pelo banco** (não pela aplicação): partidas dobradas, imutabilidade, período, anti-backdating e isolamento multi-ente.
>
> **DDL ilustrativo**, a validar contra PCASP/MCASP vigentes e a estratégia de particionamento por porte de ente. `stress test` das travas em [README §6](./README.md#6-stress-test-lógico-caso-de-uso--cenário-de-falha).

## Tabelas

```sql
create extension if not exists "uuid-ossp";

-- 1. Ente (raiz multi-tenant)
create table ente (
  id          uuid primary key default uuid_generate_v4(),
  cnpj        varchar(14) not null unique,
  nome        text not null,
  esfera      text not null check (esfera in ('uniao','estado','df','municipio')),
  criado_em   timestamptz not null default clock_timestamp()
);

-- 2. Plano de contas (PCASP)
create table conta_pcasp (
  id                   uuid primary key default uuid_generate_v4(),
  ente_id              uuid not null references ente(id),
  codigo               varchar(20) not null,
  descricao            text not null,
  natureza_informacao  text not null check (natureza_informacao in ('patrimonial','orcamentaria','controle')),
  natureza_saldo       char(1) not null check (natureza_saldo in ('D','C')),
  escrituravel         boolean not null default true,   -- só conta analítica recebe lançamento
  conta_pai_id         uuid references conta_pcasp(id),
  unique (ente_id, codigo)
);

-- 3. Período contábil (controla o fechamento)
create table periodo_contabil (
  id            uuid primary key default uuid_generate_v4(),
  ente_id       uuid not null references ente(id),
  exercicio     int not null,
  mes           int not null check (mes between 1 and 13),  -- 13 = encerramento do exercício
  status        text not null default 'aberto' check (status in ('aberto','encerrado')),
  encerrado_em  timestamptz,
  unique (ente_id, exercicio, mes)
);

-- 4. Fato contábil (o evento; imutável após consolidado)
create table fato_contabil (
  id                   uuid primary key default uuid_generate_v4(),
  ente_id              uuid not null references ente(id),
  numero_seq           bigint not null,                              -- sequencial cronológico gapless por ente
  data_competencia     date not null,                               -- fato gerador (Lei 4.320 art. 35)
  data_hora_registro   timestamptz not null default clock_timestamp(),  -- relógio do SERVIDOR (anti-backdating)
  periodo_id           uuid not null references periodo_contabil(id),
  tipo_evento          text not null,                               -- empenho|liquidacao|pagamento|receita|estorno|abertura
  historico            text not null,
  origem               text not null,                               -- módulo/integração de origem
  fato_estornado_id    uuid references fato_contabil(id),           -- vínculo do estorno ao original
  unique (ente_id, numero_seq)
);

-- 5. Lançamento (partida: débito/crédito)
create table lancamento (
  id         uuid primary key default uuid_generate_v4(),
  ente_id    uuid not null references ente(id),
  fato_id    uuid not null references fato_contabil(id),
  conta_id   uuid not null references conta_pcasp(id),
  natureza   char(1) not null check (natureza in ('D','C')),
  valor      numeric(18,2) not null check (valor > 0)               -- dinheiro em DECIMAL, nunca float
);
create index on lancamento (fato_id);
create index on lancamento (ente_id, conta_id);
```

## Trava 1 — partidas dobradas (Σdébito = Σcrédito por fato)

Constraint trigger **diferida**: os lançamentos de um fato são inseridos na mesma transação e a soma é conferida **no commit**.

```sql
create or replace function checa_partidas_dobradas() returns trigger as $$
declare v_deb numeric(18,2); v_cred numeric(18,2);
begin
  select coalesce(sum(valor) filter (where natureza='D'),0),
         coalesce(sum(valor) filter (where natureza='C'),0)
    into v_deb, v_cred
    from lancamento where fato_id = new.fato_id;
  if v_deb <> v_cred then
    raise exception 'Partidas dobradas violadas no fato %: D=% C=%', new.fato_id, v_deb, v_cred;
  end if;
  return null;
end; $$ language plpgsql;

create constraint trigger trg_partidas_dobradas
  after insert on lancamento
  deferrable initially deferred
  for each row execute function checa_partidas_dobradas();
```

## Trava 2 — imutabilidade (append-only; correção por estorno)

Defesa em profundidade: **trigger** que bloqueia mutação **+** ausência de grant de `UPDATE`/`DELETE` para o papel da aplicação.

```sql
create or replace function bloqueia_mutacao() returns trigger as $$
begin
  raise exception 'Registro consolidado e imutavel (append-only). Corrija por estorno.';
end; $$ language plpgsql;

create trigger trg_imutavel_fato   before update or delete on fato_contabil
  for each row execute function bloqueia_mutacao();
create trigger trg_imutavel_lanc   before update or delete on lancamento
  for each row execute function bloqueia_mutacao();

-- papel da aplicação não recebe UPDATE/DELETE nessas tabelas
revoke update, delete on fato_contabil, lancamento from app_role;
grant  insert, select on fato_contabil, lancamento to   app_role;
```

## Trava 3 — período encerrado (bloqueia registro retroativo)

```sql
create or replace function checa_periodo_aberto() returns trigger as $$
begin
  if (select status from periodo_contabil where id = new.periodo_id) = 'encerrado' then
    raise exception 'Periodo encerrado: registro vedado (corrija por estorno no periodo aberto).';
  end if;
  return new;
end; $$ language plpgsql;

create trigger trg_periodo_aberto before insert on fato_contabil
  for each row execute function checa_periodo_aberto();
```

## Trava 4 — isolamento multi-ente (RLS deny-by-default)

RLS **forçada** em todas as tabelas com `ente_id`; a aplicação faz `set local app.ente_id` por transação. Sem a variável, **nada é visível** (deny-by-default).

```sql
alter table conta_pcasp        enable row level security;  alter table conta_pcasp        force row level security;
alter table periodo_contabil   enable row level security;  alter table periodo_contabil   force row level security;
alter table fato_contabil      enable row level security;  alter table fato_contabil      force row level security;
alter table lancamento         enable row level security;  alter table lancamento         force row level security;

create policy tenant_isolation on fato_contabil
  using      (ente_id = current_setting('app.ente_id')::uuid)
  with check (ente_id = current_setting('app.ente_id')::uuid);
-- policy análoga em conta_pcasp, periodo_contabil, lancamento

-- por requisição/transação:  set local app.ente_id = '<uuid-do-ente>';
```

## Numeração sequencial cronológica (gapless)

Sequences do PostgreSQL deixam **buracos** em rollback; a lei pede numeração cronológica sem lacuna. Usa-se um contador por ente com trava de linha na mesma transação:

```sql
create table contador_fato (
  ente_id  uuid primary key references ente(id),
  proximo  bigint not null default 1
);
-- ao inserir o fato (dentro da tx):
--   update contador_fato set proximo = proximo + 1
--   where ente_id = :ente returning proximo - 1  ->  numero_seq
```

## Saldos (derivados, nunca gravados como verdade)

O saldo é **calculado** dos lançamentos — a base é a fonte única; saldos materializados (se houver) são cache reconstruível.

```sql
create view saldo_conta as
select ente_id, conta_id,
       sum(case when natureza='D' then valor else -valor end) as saldo_devedor_liquido
from lancamento
group by ente_id, conta_id;
-- interpretação do sinal conforme conta_pcasp.natureza_saldo (D/C)
```

## Como o guardião testa isto

Cada trava tem um **teste de integração** que tenta violá-la e **espera rejeição** — é o guardião de build no nível do banco (ver [guardrails](./README.md#8-guardrails-automatizados)):

| Teste | Esperado |
| --- | --- |
| Inserir fato com Σdébito ≠ Σcrédito | Exceção no commit (trava 1) |
| `update`/`delete` em fato/lançamento consolidado | Exceção (trava 2) |
| Inserir fato em período encerrado | Exceção (trava 3) |
| Consultar sem `app.ente_id` ou de outro ente | Zero linhas / negado (trava 4) |
| `numero_seq` duplicado / com buraco | Violação de unique / detecção |
| Persistir `valor` com mais de 2 casas ou negativo | Violação de check |

## Notas e pontos abertos

- **Particionamento** por `exercicio` (e/ou `ente_id`) para escala de entes grandes — reavaliar por porte ([ADR-0001](./adr/0001-base-unica-postgresql.md)).
- **Trilha de auditoria** é store **segregado** (WORM + hash-chain), não estas tabelas ([ADR-0005](./adr/0005-trilha-append-only-hash-chain.md)).
- Naturezas e classificações seguem **PCASP/MCASP vigentes** — validar antes de fixar domínios.
- Triggers são defesa-em-profundidade; a garantia primária é **least-privilege** (papel sem UPDATE/DELETE) + RLS.

---

[← Arquitetura técnica](./README.md) · [Modelo de dados](../10-modelo-dados.md) · [Motor de partidas dobradas (domínio)](./motor-razao-partidas-dobradas.md) · [ADRs](./adr/)
