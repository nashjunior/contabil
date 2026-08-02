package br.contabil.plataforma.domain.transparencia;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Página keyset com metadados de posição exigidos pelo contrato público. */
public record PaginaTransparenciaPublica(
        List<PublicacaoTransparencia> itens,
        Optional<String> proximoCursor,
        boolean haMais,
        long contagemAproximada,
        Optional<Instant> ultimaAtualizacao) {

    public PaginaTransparenciaPublica {
        itens = List.copyOf(Objects.requireNonNull(itens, "itens"));
        Objects.requireNonNull(proximoCursor, "proximoCursor");
        if (contagemAproximada < 0) {
            throw new IllegalArgumentException("contagem aproximada não pode ser negativa");
        }
        Objects.requireNonNull(ultimaAtualizacao, "ultimaAtualizacao");
    }
}
