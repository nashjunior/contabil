package br.contabil.consulta;

import java.util.List;
import java.util.Map;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.razao.domain.DemonstracoesDcasp;
import br.contabil.razao.domain.LinhaDemonstracaoDcasp;

/**
 * Corpo HTTP de {@code GET .../razao/demonstracoes-dcasp}. Valores monetários
 * seguem o {@link DinheiroJacksonModule}: string decimal de 2 casas.
 */
record DemonstracoesDcaspResponse(
        int exercicio,
        Demonstracao balancoOrcamentario,
        Demonstracao balancoFinanceiro,
        Demonstracao balancoPatrimonial,
        Demonstracao dvp) {

    static DemonstracoesDcaspResponse de(DemonstracoesDcasp demonstracoes) {
        return new DemonstracoesDcaspResponse(
                demonstracoes.exercicio(),
                Demonstracao.de(demonstracoes.balancoOrcamentario().linhas()),
                Demonstracao.de(demonstracoes.balancoFinanceiro().linhas()),
                Demonstracao.de(demonstracoes.balancoPatrimonial().linhas()),
                Demonstracao.de(demonstracoes.dvp().linhas()));
    }

    record Demonstracao(List<Linha> linhas) {

        static Demonstracao de(List<LinhaDemonstracaoDcasp> linhas) {
            return new Demonstracao(linhas.stream().map(Linha::de).toList());
        }
    }

    record Linha(String codigo, String descricao, Map<String, Dinheiro> valores) {

        static Linha de(LinhaDemonstracaoDcasp linha) {
            return new Linha(linha.codigo(), linha.descricao(), linha.valores());
        }
    }
}
