package br.contabil.razao.domain;

/** Estado do período contábil (razao-contabil-schema.md, coluna {@code periodo_contabil.status}). */
public enum StatusPeriodo {
    ABERTO("aberto"),
    ENCERRADO("encerrado");

    private final String codigo;

    StatusPeriodo(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static StatusPeriodo deCodigo(String codigo) {
        for (StatusPeriodo status : values()) {
            if (status.codigo.equals(codigo)) {
                return status;
            }
        }
        throw new IllegalArgumentException("status de período inválido: " + codigo);
    }
}
