package br.contabil.razao.domain;

/**
 * As <b>9 informações complementares (IC)</b> da MSC (Portaria STN 642/2019,
 * Regras Gerais MSC), cada linha da matriz sendo conta PCASP último nível × até
 * 6 IC × {@code TIPO_VALOR} × {@code NATUREZA} × {@code VALOR} (ADR-0048/ADR-0050).
 *
 * <p>Este enum é o vocabulário fechado das IC e classifica <b>como cada uma é
 * obtida</b> ({@link Origem}) — a decisão de modelagem da RAZ-250 (ADR-0057):
 * <ul>
 *   <li>{@link Origem#LANCAMENTO}: capturada na partida (dimensão do
 *       {@link Lancamento} via {@link InformacoesComplementares}) — {@code FR, CO, NR, ND, FS, AI};</li>
 *   <li>{@link Origem#FATO}: capturada no evento (fato-wide, no {@link FatoContabil}) — {@code PO};</li>
 *   <li>{@link Origem#DERIVADA}: <b>não</b> capturada por partida — calculada na
 *       projeção a partir de saldos patrimoniais/DDR (classes 7/8, RAZ-225) — {@code FP, DC}.</li>
 * </ul>
 *
 * <p>A regra "conta X exige IC Y" (condicional por conta, ADR-0050) é aplicada na
 * escrituração pela origem; a projeção MSC ({@code MatrizSaldosContabeisPort},
 * RAZ-251/ADR-0056) apenas agrupa por estas dimensões, sem reclassificar.
 */
public enum InformacaoComplementar {

    /** Poder/Órgão — unidade emitente (fato-wide). */
    PO("Poder/Órgão", Origem.FATO),
    /** Superávit financeiro — derivado de saldos patrimoniais/DDR. */
    FP("Superávit financeiro", Origem.DERIVADA),
    /** Dívida consolidada — derivada de saldos patrimoniais/DDR. */
    DC("Dívida consolidada", Origem.DERIVADA),
    /** Fonte de recursos (vinculação) — já mecanizada em RAZ-225/ADR-0054. */
    FR("Fonte de recursos", Origem.LANCAMENTO),
    /** Execução orçamentária. */
    CO("Execução orçamentária", Origem.LANCAMENTO),
    /** Natureza da receita. */
    NR("Natureza da receita", Origem.LANCAMENTO),
    /** Natureza da despesa. */
    ND("Natureza da despesa", Origem.LANCAMENTO),
    /** Função/subfunção. */
    FS("Função/subfunção", Origem.LANCAMENTO),
    /** Ano de inscrição de Restos a Pagar. */
    AI("Ano de inscrição de RP", Origem.LANCAMENTO);

    /** Como a IC entra no razão — determina onde (e se) ela é capturada. */
    public enum Origem {
        /** Dimensão da partida ({@link Lancamento}). */
        LANCAMENTO,
        /** Dimensão do evento ({@link FatoContabil}), fato-wide. */
        FATO,
        /** Não capturada — derivada na projeção a partir de saldos. */
        DERIVADA
    }

    private final String descricao;
    private final Origem origem;

    InformacaoComplementar(String descricao, Origem origem) {
        this.descricao = descricao;
        this.origem = origem;
    }

    public String descricao() {
        return descricao;
    }

    public Origem origem() {
        return origem;
    }

    /** {@code true} para as IC capturadas na escrituração (LANCAMENTO/FATO); {@code false} para as derivadas. */
    public boolean capturada() {
        return origem != Origem.DERIVADA;
    }
}
