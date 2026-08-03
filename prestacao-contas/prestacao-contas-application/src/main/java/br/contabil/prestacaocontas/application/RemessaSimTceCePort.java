package br.contabil.prestacaocontas.application;

import java.util.List;

import br.contabil.prestacaocontas.domain.DimensaoOrganizacionalSimTceCe;
import br.contabil.prestacaocontas.domain.RemessaSimTceCe;
import br.contabil.razao.domain.Balancete;

/** Porta de geração do arquivo PGI/SIM a partir de uma projeção do razão. */
public interface RemessaSimTceCePort {

    RemessaSimTceCe gerarTabela308(Balancete balancete, List<DimensaoOrganizacionalSimTceCe> dimensoes);
}
