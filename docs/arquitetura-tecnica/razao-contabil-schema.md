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
  id                   uuid not null default uuid_generate_v4(),
  ente_id              uuid not null references ente(id),
  codigo               varchar(20) not null,
  descricao            text not null,
  natureza_informacao  text not null check (natureza_informacao in ('patrimonial','orcamentaria','controle')),
  natureza_saldo       char(1) not null check (natureza_saldo in ('D','C')),
  escrituravel         boolean not null default true,   -- só conta analítica recebe lançamento
  conta_pai_id         uuid,
  primary key (id),
  unique (ente_id, id),                                 -- ancora a FK composta abaixo (trava 4b)
  unique (ente_id, codigo),
  foreign key (ente_id, conta_pai_id) references conta_pcasp (ente_id, id)
);

-- 3. Período contábil (controla o fechamento)
create table periodo_contabil (
  id            uuid not null default uuid_generate_v4(),
  ente_id       uuid not null references ente(id),
  exercicio     int not null,
  mes           int not null check (mes between 1 and 13),  -- 13 = encerramento do exercício
  status        text not null default 'aberto' check (status in ('aberto','encerrado')),
  encerrado_em  timestamptz,
  primary key (id),
  unique (ente_id, id),                                 -- ancora a FK composta de fato_contabil.periodo_id (trava 4b)
  unique (ente_id, exercicio, mes)
);

-- 4. Fato contábil (o evento; imutável após consolidado)
create table fato_contabil (
  id                   uuid not null default uuid_generate_v4(),
  ente_id              uuid not null references ente(id),
  numero_seq           bigint not null,                              -- sequencial cronológico gapless por ente
  data_competencia     date not null,                               -- fato gerador (Lei 4.320 art. 35)
  data_hora_registro   timestamptz not null default clock_timestamp(),  -- relógio do SERVIDOR (anti-backdating)
  periodo_id           uuid not null,
  tipo_evento          text not null check (tipo_evento in ('empenho','liquidacao','pagamento','receita','estorno','abertura')),
  historico            text not null,
  origem               text not null,                               -- módulo/integração de origem
  fato_estornado_id    uuid,                                        -- vínculo do estorno ao original
  primary key (id),
  unique (ente_id, id),                                             -- ancora a FK composta de lancamento.fato_id (trava 4b)
  unique (ente_id, numero_seq),
  foreign key (ente_id, periodo_id) references periodo_contabil (ente_id, id),
  foreign key (ente_id, fato_estornado_id) references fato_contabil (ente_id, id)
);

-- 5. Lançamento (partida: débito/crédito)
create table lancamento (
  id         uuid primary key default uuid_generate_v4(),
  ente_id    uuid not null references ente(id),
  fato_id    uuid not null,
  conta_id   uuid not null,
  natureza   char(1) not null check (natureza in ('D','C')),
  valor      numeric(18,2) not null check (valor > 0),              -- dinheiro em DECIMAL, nunca float
  foreign key (ente_id, fato_id) references fato_contabil (ente_id, id),
  foreign key (ente_id, conta_id) references conta_pcasp (ente_id, id)
);
create index on lancamento (fato_id);
create index on lancamento (ente_id, conta_id);
```

### `ente` — cadastro é operação administrativa, fora do `app_role` (RAZ-17)

`ente` é a **raiz** multi-tenant: ao contrário das demais tabelas, não tem coluna `ente_id` própria para a trava 4 filtrar — o próprio `id` é o tenant. Por isso `app_role` **não recebe grant nenhum** nela (nem `select`, nem `insert`): um `select` sem RLS vazaria o catálogo inteiro de entes (nome/CNPJ/esfera de todo tenant) para qualquer sessão autenticada como `app_login`, e um `insert` deixaria o login de runtime — que só deveria agir dentro do ente da sessão (`app.ente_id`) — cadastrar novo tenant sem controle administrativo algum.

Mesmo padrão já usado em `contador_fato`: sem grant direto ao papel de runtime, acesso mediado fora dele. O cadastro do primeiro ente é feito hoje pela credencial de migration/administração (ver seed de teste em `RazaoContabilTravasTest`/`VazamentoCrossTenantRlsTest`); um fluxo de onboarding self-service, se vier a existir, é uma decisão de produto separada (novo papel/rota administrativa), não uma extensão do `app_role`.

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
  -- filtro por ente_id é defesa em profundidade (trava 4b já impede via FK
  -- composta referenciar período de outro ente).
  if (select status from periodo_contabil where id = new.periodo_id and ente_id = new.ente_id) = 'encerrado' then
    raise exception 'Periodo encerrado: registro vedado (corrija por estorno no periodo aberto).';
  end if;
  return new;
end; $$ language plpgsql;

create trigger trg_periodo_aberto before insert on fato_contabil
  for each row execute function checa_periodo_aberto();
```

## Trava 3b — anti-backdating (data_hora_registro é sempre o relógio do servidor)

O `default clock_timestamp()` na coluna só vale quando ela é **omitida** do `insert` — um `insert` explícito poderia informar qualquer `timestamptz` passado. Um trigger `before insert` **sobrescreve sempre**, fechando essa brecha (a garantia fica no banco, não só no default):

```sql
create or replace function forca_data_hora_registro() returns trigger as $$
begin
  new.data_hora_registro := clock_timestamp();
  return new;
end; $$ language plpgsql;

create trigger trg_anti_backdating before insert on fato_contabil
  for each row execute function forca_data_hora_registro();
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

### Trava 4b — FK composta (fecha o bypass de RLS via constraint)

FK do Postgres roda **como dono da tabela** (`checa fk` interno), então **ignora RLS** mesmo com `force row level security`. Uma FK simples (`fato_contabil.periodo_id references periodo_contabil(id)`) deixa o app_role referenciar a **PK de outro ente** — a violação só apareceria depois, de forma indireta (ex.: `checa_periodo_aberto` lendo uma linha invisível sob RLS como `NULL`, nunca `'encerrado'`).

Correção: **PK/unique composta `(ente_id, id)`** em toda tabela referenciada por FK entre tabelas com `ente_id`, e a **FK também composta** `(ente_id, coluna_id)`. Assim o próprio banco (não a aplicação, não o trigger) rejeita a referência cross-tenant no `insert`/`update`, antes de qualquer trigger rodar:

- `conta_pcasp.conta_pai_id` → `(ente_id, conta_pai_id) references conta_pcasp (ente_id, id)`
- `periodo_contabil` / `fato_contabil` / `conta_pcasp` ganham `unique (ente_id, id)` para ancorar as FKs acima
- `fato_contabil.periodo_id` → `(ente_id, periodo_id) references periodo_contabil (ente_id, id)`
- `fato_contabil.fato_estornado_id` → `(ente_id, fato_estornado_id) references fato_contabil (ente_id, id)`
- `lancamento.fato_id` → `(ente_id, fato_id) references fato_contabil (ente_id, id)`
- `lancamento.conta_id` → `(ente_id, conta_id) references conta_pcasp (ente_id, id)`

## Numeração sequencial cronológica (gapless)

Sequences do PostgreSQL deixam **buracos** em rollback; a lei pede numeração cronológica sem lacuna. Usa-se um contador por ente com trava de linha na mesma transação, via função **`security definer`** (o `app_role` não tem acesso direto a `contador_fato`):

```sql
create table contador_fato (
  ente_id  uuid primary key references ente(id),
  proximo  bigint not null default 1
);

-- SEM parâmetro de ente: o ente é derivado de current_setting('app.ente_id'),
-- a MESMA variável de sessão que a RLS usa — nunca de um argumento passado
-- pelo chamador. Sendo security definer, a função roda como dono da tabela e
-- ignora RLS; se aceitasse p_ente_id como argumento, um app_login logado como
-- ente A poderia incrementar a sequência do ente B só passando o UUID de B
-- (RAZ-14).
create function proximo_numero_seq() returns bigint
language plpgsql security definer set search_path = pg_catalog, public as $$
declare
  v_ente_id uuid := current_setting('app.ente_id', true)::uuid;
  v_numero  bigint;
begin
  update contador_fato set proximo = proximo + 1
   where ente_id = v_ente_id
  returning proximo - 1 into v_numero;
  if not found then
    raise exception 'Ente % sem contador de fato inicializado', v_ente_id;
  end if;
  return v_numero;
end; $$;
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
| Inserir fato com `data_hora_registro` explícita no passado/futuro | Coluna sobrescrita com `clock_timestamp()` do servidor (trava 3b) |
| Consultar sem `app.ente_id` ou de outro ente | Zero linhas / negado (trava 4) |
| Inserir fato/lançamento referenciando período/fato/conta de **outro ente** (`periodo_id`, `fato_id`, `conta_id`, `conta_pai_id`, `fato_estornado_id`) | Violação de FK composta (trava 4b), não silêncio via RLS |
| `numero_seq` duplicado / com buraco | Violação de unique / detecção |
| Chamar `proximo_numero_seq()` como ente A tentando numerar para o ente B | Ignorado — sempre incrementa o contador do `app.ente_id` da sessão, nunca um ente arbitrário |
| Persistir `valor` com mais de 2 casas ou negativo | Violação de check |
| Persistir `tipo_evento` fora do domínio (`empenho\|liquidacao\|pagamento\|receita\|estorno\|abertura`) | Violação de check |

## Notas e pontos abertos

- **Particionamento** por `exercicio` (e/ou `ente_id`) para escala de entes grandes — reavaliar por porte ([ADR-0001](./adr/0001-base-unica-postgresql.md)).
- **Trilha de auditoria** é store **segregado** (WORM + hash-chain), não estas tabelas ([ADR-0005](./adr/0005-trilha-append-only-hash-chain.md)).
- Naturezas e classificações seguem **PCASP/MCASP vigentes** — validar antes de fixar domínios.
- Triggers são defesa-em-profundidade; a garantia primária é **least-privilege** (papel sem UPDATE/DELETE) + RLS.

---

[← Arquitetura técnica](./README.md) · [Modelo de dados](../10-modelo-dados.md) · [Motor de partidas dobradas (domínio)](./motor-razao-partidas-dobradas.md) · [ADRs](./adr/)
