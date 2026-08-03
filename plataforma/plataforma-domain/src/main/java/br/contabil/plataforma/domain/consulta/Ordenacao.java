package br.contabil.plataforma.domain.consulta;

import java.util.Objects;

/** Campo e direção de ordenação pedidos por um contrato de consulta. */
public record Ordenacao(String campo, Direcao direcao) {

    public Ordenacao {
        if (campo == null || campo.isBlank()) {
            throw new IllegalArgumentException("campo de ordenação é obrigatório");
        }
        campo = campo.trim();
        Objects.requireNonNull(direcao, "direção de ordenação");
    }
}
