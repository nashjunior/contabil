package br.contabil.plataforma.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Valor monetário decimal — invariante NÃO-NEGOCIÁVEL do sistema contábil:
 * dinheiro nunca é representado por ponto flutuante. Escala fixa em 2 casas,
 * arredondamento {@link RoundingMode#HALF_EVEN} (bankers' rounding).
 *
 * <p>Seed do shared kernel (RAZ-1). Moeda/cambio, se necessários, entram em ADR
 * dedicada — por ora o sistema opera em moeda única (BRL, SIAFIC).
 */
public record Dinheiro(BigDecimal valor) implements Comparable<Dinheiro> {

    public static final int ESCALA = 2;
    public static final RoundingMode ARREDONDAMENTO = RoundingMode.HALF_EVEN;

    public Dinheiro {
        Objects.requireNonNull(valor, "valor monetário não pode ser nulo");
        valor = valor.setScale(ESCALA, ARREDONDAMENTO);
    }

    public static Dinheiro de(String valor) {
        return new Dinheiro(new BigDecimal(valor));
    }

    public static Dinheiro zero() {
        return new Dinheiro(BigDecimal.ZERO);
    }

    public Dinheiro somar(Dinheiro outro) {
        return new Dinheiro(this.valor.add(outro.valor));
    }

    public Dinheiro subtrair(Dinheiro outro) {
        return new Dinheiro(this.valor.subtract(outro.valor));
    }

    public boolean isZero() {
        return this.valor.signum() == 0;
    }

    @Override
    public int compareTo(Dinheiro outro) {
        return this.valor.compareTo(outro.valor);
    }
}
