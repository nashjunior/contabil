-- Persistencia das informacoes complementares restantes da MSC no razao — RAZ-267.
-- Ref.: ADR-0057. Migracao aditiva e retrocompativel: todas as colunas sao
-- nullable porque IC e condicional por conta/fato, e nao ha retrofit em F0.

alter table fato_contabil
  add column poder_orgao varchar(20);

comment on column fato_contabil.poder_orgao is
  'Poder/Orgao (PO) — IC fato-wide da MSC, unidade emitente. Nullable: dimensao condicional por fato. [REVALIDAR] formato no leiaute MSC/MCASP vigente.';

alter table lancamento
  add column execucao_orcamentaria varchar(20),
  add column natureza_receita varchar(20),
  add column natureza_despesa varchar(20),
  add column funcao_subfuncao varchar(20),
  add column ano_inscricao_rp integer,
  add constraint ck_lancamento_ano_inscricao_rp
    check (ano_inscricao_rp is null or ano_inscricao_rp between 1964 and 9999);

comment on column lancamento.execucao_orcamentaria is
  'Execucao orcamentaria (CO) — IC de partida da MSC. Nullable: dimensao condicional por conta. [REVALIDAR] formato no leiaute MSC/MCASP vigente.';

comment on column lancamento.natureza_receita is
  'Natureza da receita (NR) — IC de partida da MSC. Nullable: dimensao condicional por conta. [REVALIDAR] formato no leiaute MSC/MCASP vigente.';

comment on column lancamento.natureza_despesa is
  'Natureza da despesa (ND) — IC de partida da MSC. Nullable: dimensao condicional por conta. [REVALIDAR] formato no leiaute MSC/MCASP vigente.';

comment on column lancamento.funcao_subfuncao is
  'Funcao/subfuncao (FS) — IC de partida da MSC. Nullable: dimensao condicional por conta. [REVALIDAR] formato no leiaute MSC/MCASP vigente.';

comment on column lancamento.ano_inscricao_rp is
  'Ano de inscricao de Restos a Pagar (AI) — IC de partida da MSC. Nullable: dimensao condicional por conta. [REVALIDAR] faixa/formato no leiaute MSC vigente.';

create index ix_fato_contabil_poder_orgao
  on fato_contabil (ente_id, poder_orgao)
  where poder_orgao is not null;

create index ix_lancamento_ic_msc
  on lancamento (
    ente_id,
    execucao_orcamentaria,
    natureza_receita,
    natureza_despesa,
    funcao_subfuncao,
    ano_inscricao_rp,
    conta_id
  )
  where execucao_orcamentaria is not null
     or natureza_receita is not null
     or natureza_despesa is not null
     or funcao_subfuncao is not null
     or ano_inscricao_rp is not null;
