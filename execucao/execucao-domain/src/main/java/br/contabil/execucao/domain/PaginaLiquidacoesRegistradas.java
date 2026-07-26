package br.contabil.execucao.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Página de liquidações registradas por cursor opaco (RAZ-121) — mesmo formato de {@link PaginaEmpenhosRegistrados}. */
public record PaginaLiquidacoesRegistradas(List<ItemLiquidacaoRegistrada> itens, Optional<String> proximoCursor) {

    public PaginaLiquidacoesRegistradas {
        itens = List.copyOf(Objects.requireNonNull(itens, "itens"));
        Objects.requireNonNull(proximoCursor, "proximoCursor (Optional, nunca null)");
    }
}
