package br.contabil.execucao.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Página de pagamentos registrados por cursor opaco (RAZ-121) — mesmo formato de {@link PaginaEmpenhosRegistrados}. */
public record PaginaPagamentosRegistrados(List<ItemPagamentoRegistrado> itens, Optional<String> proximoCursor) {

    public PaginaPagamentosRegistrados {
        itens = List.copyOf(Objects.requireNonNull(itens, "itens"));
        Objects.requireNonNull(proximoCursor, "proximoCursor (Optional, nunca null)");
    }
}
