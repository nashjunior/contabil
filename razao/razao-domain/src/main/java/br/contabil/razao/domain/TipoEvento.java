package br.contabil.razao.domain;

/**
 * Tipo do evento que originou o fato contábil (razao-contabil-schema.md,
 * coluna {@code fato_contabil.tipo_evento}). O motor de razão apenas registra;
 * quem produz o fato (execução orçamentária/financeira) escolhe o tipo.
 */
public enum TipoEvento {
    EMPENHO("empenho"),
    LIQUIDACAO("liquidacao"),
    PAGAMENTO("pagamento"),
    RECEITA("receita"),
    ESTORNO("estorno"),
    ENCERRAMENTO("encerramento"),
    ABERTURA("abertura"),
    INSCRICAO_RESTOS_A_PAGAR("inscricao_restos_a_pagar"),
    CANCELAMENTO_RESTOS_A_PAGAR("cancelamento_restos_a_pagar"),
    ENCERRAMENTO_DDR("encerramento_ddr");

    private final String codigo;

    TipoEvento(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static TipoEvento deCodigo(String codigo) {
        for (TipoEvento tipo : values()) {
            if (tipo.codigo.equals(codigo)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("tipo_evento inválido: " + codigo);
    }
}
