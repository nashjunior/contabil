package br.contabil.prestacaocontas.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Campo parametrizado do leiaute PGI/SIM. A ordem da lista no layout é a ordem
 * serializada; o adapter não conhece posições oficiais no código.
 */
public record CampoLayoutRemessaSimTceCe(String nome, Optional<String> origem, Optional<String> valorFixo) {

    public CampoLayoutRemessaSimTceCe {
        Objects.requireNonNull(nome, "nome do campo");
        Objects.requireNonNull(origem, "origem");
        Objects.requireNonNull(valorFixo, "valor fixo");
        nome = nome.strip();
        if (nome.isEmpty()) {
            throw new IllegalArgumentException("nome do campo do SIM não pode ser vazio");
        }
        origem = origem.map(String::strip).filter(valor -> !valor.isEmpty());
        valorFixo = valorFixo.map(String::strip);
        if (origem.isEmpty() == valorFixo.isEmpty()) {
            throw new IllegalArgumentException(
                    "campo do SIM deve declarar exatamente uma origem ou um valor fixo: " + nome);
        }
    }

    public static CampoLayoutRemessaSimTceCe origem(String nome, String origem) {
        return new CampoLayoutRemessaSimTceCe(nome, Optional.of(origem), Optional.empty());
    }

    public static CampoLayoutRemessaSimTceCe fixo(String nome, String valor) {
        return new CampoLayoutRemessaSimTceCe(nome, Optional.empty(), Optional.of(valor));
    }
}
