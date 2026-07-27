-- RAZ-148: listagem GET /dotacoes por ente/exercicio com keyset por classificacao.
create index ix_dotacao_ente_exercicio
    on dotacao (ente_id, exercicio, classificacao_orcamentaria, id);
