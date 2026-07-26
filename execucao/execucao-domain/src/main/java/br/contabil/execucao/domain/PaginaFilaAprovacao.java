package br.contabil.execucao.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Página da fila de aprovação por cursor opaco (ADR-0029 §1): envelope {@code
 * {itens, proximoCursor}}. Cursor (não offset/{@code total}) de propósito — a
 * fila muda sob o leitor à medida que itens são decididos; keyset dá páginas
 * estáveis e evita um {@code COUNT(*)} da fila inteira a cada leitura
 * (ADR-0007, read model não onera a escrita). {@code proximoCursor} vazio =
 * última página.
 */
public record PaginaFilaAprovacao(List<ItemFilaAprovacao> itens, Optional<String> proximoCursor) {

    public PaginaFilaAprovacao {
        itens = List.copyOf(Objects.requireNonNull(itens, "itens"));
        Objects.requireNonNull(proximoCursor, "proximoCursor (Optional, nunca null)");
    }
}
