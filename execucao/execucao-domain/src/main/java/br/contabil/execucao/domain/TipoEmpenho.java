package br.contabil.execucao.domain;

/**
 * Tipo do empenho (Lei 4.320/1964 art. 60) — governa a cardinalidade com a
 * liquidação: ordinário fecha em uma parcela; estimativo/global admitem N
 * liquidações (10-modelo-dados.md §Tipos de empenho).
 */
public enum TipoEmpenho {
    ORDINARIO("ordinario"),
    ESTIMATIVO("estimativo"),
    GLOBAL("global");

    private final String codigo;

    TipoEmpenho(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static TipoEmpenho deCodigo(String codigo) {
        for (TipoEmpenho tipo : values()) {
            if (tipo.codigo.equals(codigo)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("tipo de empenho inválido: " + codigo);
    }
}
