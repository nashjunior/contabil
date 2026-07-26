package br.contabil.execucao.domain;

/**
 * Tipo do crédito adicional (Lei 4.320/1964 arts. 41-42, CF art. 167) — a
 * autorização legal que amplia {@code valorAutorizado} de uma dotação já
 * fixada na LOA (10-modelo-dados.md, entidade {@code CREDITO_ADICIONAL}).
 */
public enum TipoCreditoAdicional {
    SUPLEMENTAR("suplementar"),
    ESPECIAL("especial"),
    EXTRAORDINARIO("extraordinario");

    private final String codigo;

    TipoCreditoAdicional(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static TipoCreditoAdicional deCodigo(String codigo) {
        for (TipoCreditoAdicional tipo : values()) {
            if (tipo.codigo.equals(codigo)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("tipo de crédito adicional inválido: " + codigo);
    }
}
