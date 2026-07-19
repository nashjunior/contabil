-- Base contábil única + schema do razão — RAZ-3.
-- Ref.: docs/arquitetura-tecnica/razao-contabil-schema.md, ADR-0001 (base única PostgreSQL),
-- ADR-0003 (multi-tenant RLS deny-by-default), ADR-0006 (dinheiro em decimal).
--
-- 4 travas (+3b) impostas pelo BANCO (defesa em profundidade, não confiar só na aplicação):
--   1. Partidas dobradas   — Σdébito = Σcrédito por fato (constraint trigger diferida)
--   2. Imutabilidade       — append-only: trigger de bloqueio + sem GRANT update/delete
--   3. Período encerrado   — bloqueia registro de fato em período fechado
--   3b. Anti-backdating    — trigger força data_hora_registro = clock_timestamp() do servidor
--   4. Isolamento multi-ente — RLS forçada, deny-by-default, em toda tabela com ente_id

create extension if not exists "uuid-ossp";

-- ============================================================================
-- Papel da aplicação (least-privilege). É um papel de GRUPO (NOLOGIN); o login
-- real usado pelo datasource é criado e associado fora desta migration (IaC/
-- secrets), com `grant app_role to <login>;`. Nunca recebe DELETE nesta base —
-- nada se apaga (correção é sempre por estorno/novo registro).
-- ============================================================================
do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'app_role') then
    create role app_role nologin;
  end if;
end;
$$;

-- ============================================================================
-- Tabelas
-- ============================================================================

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
  escrituravel         boolean not null default true,   -- só conta analítica recebe lançamento (RAZ-4 valida na aplicação)
  conta_pai_id         uuid,
  primary key (id),
  unique (ente_id, id),                                 -- ancora a FK composta abaixo (RAZ-13: FK simples roda como dono e ignora RLS)
  unique (ente_id, codigo),
  foreign key (ente_id, conta_pai_id) references conta_pcasp (ente_id, id)
);
create index ix_conta_pcasp_pai on conta_pcasp (conta_pai_id);

-- 3. Período contábil (controla o fechamento)
create table periodo_contabil (
  id            uuid not null default uuid_generate_v4(),
  ente_id       uuid not null references ente(id),
  exercicio     int not null,
  mes           int not null check (mes between 1 and 13),  -- 13 = encerramento do exercício
  status        text not null default 'aberto' check (status in ('aberto','encerrado')),
  encerrado_em  timestamptz,
  primary key (id),
  unique (ente_id, id),                                 -- ancora a FK composta de fato_contabil.periodo_id (RAZ-13)
  unique (ente_id, exercicio, mes)
);

-- 4. Contador do fato — sustenta a numeração sequencial cronológica gapless
--    por ente (sequences do Postgres deixam buraco em rollback; a lei pede
--    numeração sem lacuna). Sem grant direto a app_role: só via
--    proximo_numero_seq() (ver abaixo).
create table contador_fato (
  ente_id  uuid primary key references ente(id),
  proximo  bigint not null default 1
);

-- 5. Fato contábil (o evento; imutável após consolidado)
create table fato_contabil (
  id                   uuid not null default uuid_generate_v4(),
  ente_id              uuid not null references ente(id),
  numero_seq           bigint not null,                              -- sequencial cronológico gapless por ente
  data_competencia     date not null,                                -- fato gerador (Lei 4.320 art. 35)
  data_hora_registro   timestamptz not null default clock_timestamp(),  -- relógio do SERVIDOR (anti-backdating)
  periodo_id           uuid not null,
  tipo_evento          text not null check (tipo_evento in ('empenho','liquidacao','pagamento','receita','estorno','abertura')),
  historico            text not null,
  origem               text not null,                                -- módulo/integração de origem
  fato_estornado_id    uuid,                                         -- vínculo do estorno ao original
  primary key (id),
  unique (ente_id, id),                                              -- ancora a FK composta de lancamento.fato_id (RAZ-13)
  unique (ente_id, numero_seq),
  foreign key (ente_id, periodo_id) references periodo_contabil (ente_id, id),
  foreign key (ente_id, fato_estornado_id) references fato_contabil (ente_id, id)
);
create index ix_fato_contabil_periodo on fato_contabil (periodo_id);
create index ix_fato_contabil_ente_data on fato_contabil (ente_id, data_competencia);

-- 6. Lançamento (partida: débito/crédito)
create table lancamento (
  id         uuid primary key default uuid_generate_v4(),
  ente_id    uuid not null references ente(id),
  fato_id    uuid not null,
  conta_id   uuid not null,
  natureza   char(1) not null check (natureza in ('D','C')),
  valor      numeric(18,2) not null check (valor > 0),               -- dinheiro em DECIMAL, nunca float (ADR-0006)
  foreign key (ente_id, fato_id) references fato_contabil (ente_id, id),
  foreign key (ente_id, conta_id) references conta_pcasp (ente_id, id)
);
create index ix_lancamento_fato on lancamento (fato_id);
create index ix_lancamento_ente_conta on lancamento (ente_id, conta_id);

-- ============================================================================
-- Trava 1 — partidas dobradas (Σdébito = Σcrédito por fato)
-- Constraint trigger DIFERIDA: os lançamentos de um fato são inseridos na
-- mesma transação e a soma é conferida no COMMIT.
-- ============================================================================
create function checa_partidas_dobradas() returns trigger as $$
declare
  v_deb  numeric(18,2);
  v_cred numeric(18,2);
begin
  select coalesce(sum(valor) filter (where natureza = 'D'), 0),
         coalesce(sum(valor) filter (where natureza = 'C'), 0)
    into v_deb, v_cred
    from lancamento
   where fato_id = new.fato_id;

  if v_deb <> v_cred then
    raise exception 'Partidas dobradas violadas no fato %: D=% C=%', new.fato_id, v_deb, v_cred;
  end if;

  return null;
end;
$$ language plpgsql;

create constraint trigger trg_partidas_dobradas
  after insert on lancamento
  deferrable initially deferred
  for each row execute function checa_partidas_dobradas();

-- ============================================================================
-- Trava 2 — imutabilidade (append-only; correção só por estorno)
-- Defesa em profundidade: trigger que bloqueia mutação + ausência de grant
-- de UPDATE/DELETE para o papel da aplicação.
-- ============================================================================
create function bloqueia_mutacao() returns trigger as $$
begin
  raise exception 'Registro consolidado e imutavel (append-only). Corrija por estorno.';
end;
$$ language plpgsql;

create trigger trg_imutavel_fato before update or delete on fato_contabil
  for each row execute function bloqueia_mutacao();
create trigger trg_imutavel_lancamento before update or delete on lancamento
  for each row execute function bloqueia_mutacao();

-- ============================================================================
-- Trava 3 — período encerrado (bloqueia registro retroativo)
-- ============================================================================
create function checa_periodo_aberto() returns trigger as $$
begin
  -- filtro por ente_id é defesa em profundidade: a FK composta de periodo_id
  -- (ver tabela fato_contabil) já impede referenciar período de outro ente;
  -- sem o filtro, a linha de outro ente seria invisível sob RLS (NULL != 'encerrado').
  if (select status from periodo_contabil where id = new.periodo_id and ente_id = new.ente_id) = 'encerrado' then
    raise exception 'Periodo encerrado: registro vedado (corrija por estorno no periodo aberto).';
  end if;
  return new;
end;
$$ language plpgsql;

create trigger trg_periodo_aberto before insert on fato_contabil
  for each row execute function checa_periodo_aberto();

-- ============================================================================
-- Trava 3b — anti-backdating (data_hora_registro é sempre o relógio do
-- SERVIDOR). O DEFAULT clock_timestamp() só vale quando a coluna é omitida;
-- um INSERT explícito poderia informar qualquer timestamptz passado. Este
-- trigger sobrescreve SEMPRE com clock_timestamp(), fechando essa brecha —
-- defesa no banco, não só o default (RAZ-14).
-- ============================================================================
create function forca_data_hora_registro() returns trigger as $$
begin
  new.data_hora_registro := clock_timestamp();
  return new;
end;
$$ language plpgsql;

create trigger trg_anti_backdating before insert on fato_contabil
  for each row execute function forca_data_hora_registro();

-- ============================================================================
-- Trava 4 — isolamento multi-ente (RLS forçada, deny-by-default)
-- Em TODA tabela com ente_id — a aplicação faz `set local app.ente_id` por
-- transação; sem a variável, nada é visível.
-- ============================================================================
alter table conta_pcasp      enable row level security; alter table conta_pcasp      force row level security;
alter table periodo_contabil enable row level security; alter table periodo_contabil force row level security;
alter table contador_fato    enable row level security; alter table contador_fato    force row level security;
alter table fato_contabil    enable row level security; alter table fato_contabil    force row level security;
alter table lancamento       enable row level security; alter table lancamento       force row level security;

create policy tenant_isolation on conta_pcasp
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

create policy tenant_isolation on periodo_contabil
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

create policy tenant_isolation on contador_fato
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

create policy tenant_isolation on fato_contabil
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

create policy tenant_isolation on lancamento
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

-- ============================================================================
-- Numeração sequencial cronológica gapless — incremento atômico via UPDATE
-- (lock de linha na mesma transação), SECURITY DEFINER porque app_role não
-- tem acesso direto a contador_fato (só via esta função).
--
-- SEM parâmetro de ente: o ente é derivado de current_setting('app.ente_id'),
-- a MESMA variável de sessão que a RLS usa — nunca de argumento passado pelo
-- chamador. Sendo SECURITY DEFINER, a função roda como dono da tabela e
-- ignora RLS; se aceitasse um p_ente_id como argumento, um app_login logado
-- como ente A poderia incrementar/consumir a sequência do ente B só passando
-- o UUID de B, quebrando o isolamento multi-ente na numeração (RAZ-14).
-- ============================================================================
create function proximo_numero_seq() returns bigint
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
  v_ente_id uuid := current_setting('app.ente_id', true)::uuid;
  v_numero  bigint;
begin
  update contador_fato
     set proximo = proximo + 1
   where ente_id = v_ente_id
  returning proximo - 1 into v_numero;

  if not found then
    raise exception 'Ente % sem contador de fato inicializado', v_ente_id;
  end if;

  return v_numero;
end;
$$;

create function inicializa_contador_fato() returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
  insert into contador_fato (ente_id, proximo) values (new.id, 1);
  return new;
end;
$$;

create trigger trg_inicializa_contador_fato
  after insert on ente
  for each row execute function inicializa_contador_fato();

-- ============================================================================
-- Saldos (derivados, nunca gravados como verdade) — a base é a fonte única;
-- saldos materializados, se houver, são cache reconstruível (ADR-0007).
-- ============================================================================
create view saldo_conta
with (security_invoker = true) as
select ente_id, conta_id,
       sum(case when natureza = 'D' then valor else -valor end) as saldo_devedor_liquido
  from lancamento
 group by ente_id, conta_id;

-- ============================================================================
-- Grants — least-privilege / deny-by-default. app_role nunca recebe DELETE
-- (nada se apaga) nem acesso direto a contador_fato (só via função acima).
-- fato_contabil/lancamento: sem UPDATE/DELETE (trava 2).
--
-- ente: SEM grant nenhum para app_role (RAZ-17). app_role roda por-transação
-- sob RLS de UM ente (app.ente_id) — mas a tabela ente é a RAIZ multi-tenant,
-- sem ente_id próprio para a trava 4 filtrar; um `select` aqui vazaria o
-- catálogo inteiro de entes (nome/cnpj/esfera de todo tenant) para qualquer
-- sessão de app_login, e um `insert` deixaria o login de runtime multi-tenant
-- cadastrar novo ente sem controle administrativo. Cadastro de ente é
-- operação ADMINISTRATIVA separada (mesmo padrão de contador_fato: sem grant
-- direto, mediada fora do papel de runtime) — hoje feita pela credencial de
-- migration/administração (ver seed de teste em RazaoContabilTravasTest), até
-- que um fluxo de onboarding dedicado seja desenhado.
-- ============================================================================
revoke all on ente, conta_pcasp, periodo_contabil, contador_fato, fato_contabil, lancamento from public;
revoke all on function proximo_numero_seq(), inicializa_contador_fato() from public;

grant select, insert, update        on conta_pcasp, periodo_contabil to app_role;
grant select, insert                on fato_contabil, lancamento     to app_role;
grant select                        on saldo_conta                   to app_role;
grant execute                       on function proximo_numero_seq() to app_role;
