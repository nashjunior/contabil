package br.contabil.razao.domain;

import java.util.Map;
import java.util.Objects;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Linha genérica de demonstração DCASP. As colunas de valor variam por
 * demonstração (ex.: previsão/execução no Balanço Orçamentário, exercício
 * atual/anterior na DVP), por isso ficam nomeadas no mapa.
 */
public record LinhaDemonstracaoDcasp(String codigo, String descricao, Map<String, Dinheiro> valores) {

    public LinhaDemonstracaoDcasp {
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(descricao, "descricao");
        Objects.requireNonNull(valores, "valores");
        valores = Map.copyOf(valores);
    }
}
