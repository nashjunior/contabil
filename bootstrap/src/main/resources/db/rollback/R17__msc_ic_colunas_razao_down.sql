-- Rollback manual de V17__msc_ic_colunas_razao.sql — RAZ-267.

drop index if exists ix_lancamento_ic_msc;
drop index if exists ix_fato_contabil_poder_orgao;

alter table lancamento
  drop constraint if exists ck_lancamento_ano_inscricao_rp,
  drop column if exists ano_inscricao_rp,
  drop column if exists funcao_subfuncao,
  drop column if exists natureza_despesa,
  drop column if exists natureza_receita,
  drop column if exists execucao_orcamentaria;

alter table fato_contabil
  drop column if exists poder_orgao;
