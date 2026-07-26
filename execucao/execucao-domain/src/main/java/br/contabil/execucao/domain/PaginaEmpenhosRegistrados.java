package br.contabil.execucao.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Página de empenhos registrados por cursor opaco (RAZ-121): envelope {@code
 * {itens, proximoCursor}}, mesmo formato de {@link PaginaFilaAprovacao}/{@code
 * PaginaContas}. Cursor (não offset/{@code total}) pelo mesmo motivo — evita
 * {@code COUNT(*)} da tabela inteira a cada leitura (ADR-0007).
 */
public record PaginaEmpenhosRegistrados(List<ItemEmpenhoRegistrado> itens, Optional<String> proximoCursor) {

    public PaginaEmpenhosRegistrados {
        itens = List.copyOf(Objects.requireNonNull(itens, "itens"));
        Objects.requireNonNull(proximoCursor, "proximoCursor (Optional, nunca null)");
    }
}
