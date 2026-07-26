package br.contabil.plataforma.domain.consulta;

import java.util.List;
import java.util.Objects;

/** Resultado paginado de consulta/read model. */
public record Paginacao<T>(int pagina, int porPagina, long total, List<T> itens) {

    public Paginacao {
        if (pagina < ConsultaPaginada.PRIMEIRA_PAGINA) {
            throw new IllegalArgumentException("página deve ser maior ou igual a 1");
        }
        if (porPagina < 1 || porPagina > ConsultaPaginada.POR_PAGINA_MAXIMO) {
            throw new IllegalArgumentException(
                    "itens por página deve estar entre 1 e " + ConsultaPaginada.POR_PAGINA_MAXIMO);
        }
        if (total < 0) {
            throw new IllegalArgumentException("total não pode ser negativo");
        }
        itens = List.copyOf(Objects.requireNonNull(itens, "itens"));
    }
}
