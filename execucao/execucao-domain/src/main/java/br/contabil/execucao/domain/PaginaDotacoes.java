package br.contabil.execucao.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Página de dotações por cursor opaco (RAZ-148), envelope {@code {itens, proximoCursor}}. */
public record PaginaDotacoes(List<ItemDotacaoComSaldo> itens, Optional<String> proximoCursor) {

    public PaginaDotacoes {
        itens = List.copyOf(Objects.requireNonNull(itens, "itens"));
        Objects.requireNonNull(proximoCursor, "proximoCursor (Optional, nunca null)");
    }
}
