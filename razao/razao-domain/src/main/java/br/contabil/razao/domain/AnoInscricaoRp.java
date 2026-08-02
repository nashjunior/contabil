package br.contabil.razao.domain;

import java.io.Serializable;

/**
 * Informação complementar {@code AI} (ano de inscrição de Restos a Pagar) de uma
 * partida do razão — o exercício em que o RP foi inscrito, capturado pela origem
 * na escrituração da inscrição/pagamento de RP (RAZ-207, ADR-0050, ADR-0057),
 * nunca reconstruído por heurística.
 *
 * <p>Dimensão de <b>ano</b> (inteiro de 4 dígitos), presente apenas nas partidas
 * que tocam contas de Restos a Pagar. A faixa aceita fica {@code [REVALIDAR]}
 * contra o leiaute MSC edição vigente; aqui só se garante um ano plausível de
 * 4 dígitos.
 */
public record AnoInscricaoRp(int ano) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Piso plausível ([REVALIDAR]): Lei 4.320/64 como âncora histórica mínima. */
    private static final int ANO_MINIMO = 1964;

    /** Teto plausível ([REVALIDAR]): 4 dígitos. */
    private static final int ANO_MAXIMO = 9999;

    public AnoInscricaoRp {
        if (ano < ANO_MINIMO || ano > ANO_MAXIMO) {
            throw new IllegalArgumentException(
                    "ano de inscrição de RP (AI) fora da faixa [%d, %d]: %d".formatted(ANO_MINIMO, ANO_MAXIMO, ano));
        }
    }

    public static AnoInscricaoRp de(int ano) {
        return new AnoInscricaoRp(ano);
    }
}
