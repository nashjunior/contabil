package br.contabil.razao.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Página de uma <i>lista</i> do §6.1 (RAZ-79 / ADR-0030 §6): {@code {itens, proximoCursor}},
 * cursor opaco (nunca offset). {@code proximoCursor} vazio ⇒ última página. Espelha
 * {@code PaginaFilaAprovacao} da execução; o catálogo de contas é uma lista paginável
 * (coleção estável, não um demonstrativo com totalização de conjunto como o balancete).
 */
public record PaginaContas(List<ContaResumo> itens, Optional<String> proximoCursor) {

    public PaginaContas {
        Objects.requireNonNull(itens, "itens");
        Objects.requireNonNull(proximoCursor, "proximoCursor (Optional, nunca null)");
        itens = List.copyOf(itens);
    }
}
