package br.contabil.execucao.domain;

import java.io.Serializable;
import java.time.LocalDate;

import br.contabil.plataforma.domain.Validacoes;

/**
 * Datas de início/fim do mandato vigente do ente — config parametrizável (ADR-0044),
 * nunca hard-coded, consultada por {@link JanelaMandatoPort} equivalente.
 *
 * <p>Base para a janela de bloqueio do art. 42 da LRF: os <b>dois últimos quadrimestres
 * do mandato</b>. A convenção STN de quadrimestre é presa ao ano-calendário (1º
 * jan-abr, 2º mai-ago, 3º set-dez) — os dois últimos são maio a dezembro do ano em que
 * o mandato termina.
 */
public record JanelaMandato(LocalDate dataInicio, LocalDate dataFim) implements Serializable {

    private static final long serialVersionUID = 1L;

    public JanelaMandato {
        Validacoes.exigirNaoNulo(dataInicio, "dataInicio");
        Validacoes.exigirNaoNulo(dataFim, "dataFim");
        if (!dataFim.isAfter(dataInicio)) {
            throw new IllegalArgumentException(
                    "data fim do mandato (%s) deve ser posterior à data início (%s)".formatted(dataFim, dataInicio));
        }
    }

    /**
     * {@code true} quando {@code data} cai nos dois últimos quadrimestres do mandato
     * (1º de maio a 31 de dezembro do ano em que {@link #dataFim} cai) — a janela em
     * que o art. 42 da LRF veda contrair obrigação sem disponibilidade de caixa por
     * fonte (ADR-0044). Fora dela, o gate é monitor, nunca bloqueia.
     */
    public boolean estaNosUltimosDoisQuadrimestres(LocalDate data) {
        Validacoes.exigirNaoNulo(data, "data");
        LocalDate inicioJanela = LocalDate.of(dataFim.getYear(), 5, 1);
        return !inicioJanela.isAfter(dataFim) && !data.isBefore(inicioJanela) && !data.isAfter(dataFim);
    }
}
