package br.contabil.razao.domain;

/**
 * Natureza de uma partida do razão — débito ou crédito (razao-contabil-schema.md,
 * coluna {@code lancamento.natureza char(1) check in ('D','C')}).
 */
public enum Natureza {
    DEBITO('D'),
    CREDITO('C');

    private final char codigo;

    Natureza(char codigo) {
        this.codigo = codigo;
    }

    public char codigo() {
        return codigo;
    }

    /** Natureza oposta — usada para inverter lançamentos num estorno (D↔C). */
    public Natureza inversa() {
        return this == DEBITO ? CREDITO : DEBITO;
    }

    public static Natureza deCodigo(char codigo) {
        return switch (codigo) {
            case 'D' -> DEBITO;
            case 'C' -> CREDITO;
            default -> throw new IllegalArgumentException("natureza inválida: " + codigo + " (esperado D ou C)");
        };
    }
}
