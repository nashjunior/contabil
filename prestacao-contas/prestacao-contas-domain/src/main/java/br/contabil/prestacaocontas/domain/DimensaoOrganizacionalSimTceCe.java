package br.contabil.prestacaocontas.domain;

import java.util.Objects;

/** Granularidade exigida pelo SIM: balancete mensal por UO/UPC/UG. */
public record DimensaoOrganizacionalSimTceCe(String unidadeOrcamentaria, String unidadePrestacaoContas, String unidadeGestora) {

    public DimensaoOrganizacionalSimTceCe {
        unidadeOrcamentaria = normalizar(unidadeOrcamentaria, "unidadeOrcamentaria");
        unidadePrestacaoContas = normalizar(unidadePrestacaoContas, "unidadePrestacaoContas");
        unidadeGestora = normalizar(unidadeGestora, "unidadeGestora");
    }

    private static String normalizar(String valor, String campo) {
        Objects.requireNonNull(valor, campo);
        String normalizado = valor.strip();
        if (normalizado.isEmpty()) {
            throw new IllegalArgumentException(campo + " não pode ser vazio");
        }
        return normalizado;
    }
}
