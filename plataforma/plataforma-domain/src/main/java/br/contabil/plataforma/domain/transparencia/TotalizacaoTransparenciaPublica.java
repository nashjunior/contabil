package br.contabil.plataforma.domain.transparencia;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Totalizações públicas sobre os mesmos filtros do detalhe. */
public record TotalizacaoTransparenciaPublica(
        List<Linha> linhas,
        BigDecimal totalGeral,
        long contagemAproximada,
        Optional<Instant> ultimaAtualizacao) {

    public TotalizacaoTransparenciaPublica {
        linhas = List.copyOf(Objects.requireNonNull(linhas, "linhas"));
        Objects.requireNonNull(totalGeral, "totalGeral");
        Objects.requireNonNull(ultimaAtualizacao, "ultimaAtualizacao");
    }

    public record Linha(String estagio, BigDecimal valor, long quantidade) {
        public Linha {
            if (estagio == null || estagio.isBlank()) {
                throw new IllegalArgumentException("estágio é obrigatório");
            }
            Objects.requireNonNull(valor, "valor");
            if (quantidade < 0) {
                throw new IllegalArgumentException("quantidade não pode ser negativa");
            }
        }
    }
}
