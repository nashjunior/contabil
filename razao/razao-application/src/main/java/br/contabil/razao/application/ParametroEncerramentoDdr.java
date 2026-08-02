package br.contabil.razao.application;

import java.util.Objects;

import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.Natureza;

/**
 * Parâmetro de encerramento de uma conta de DDR (classes 7/8) por fonte: define a conta
 * de origem (disponibilidade utilizada), a conta de destino (controle da disponibilidade,
 * classe 7) e a natureza do lançamento na conta de destino.
 *
 * <p>Os códigos PCASP concretos são {@code [REVALIDAR]} no MCASP edição vigente antes de
 * configurar em produção. Exemplo (não normativo, IPC 03/STN rev. 2017 §91):
 * <ul>
 *   <li>origem 8.2.1.1.4.00.00 (DDR Utilizada), destino 7.2.1.1.X.00.00 (Controle da
 *       Disponibilidade de Recursos), natureza destino = CREDITO.
 * </ul>
 *
 * @param contaOrigem     conta cujos saldos por fonte serão encerrados — ex.: DDR Utilizada.
 * @param contaDestino    conta de controle que recebe a contrapartida do encerramento.
 * @param naturezaDestino natureza do lançamento na conta de destino (a origem recebe a inversa).
 */
public record ParametroEncerramentoDdr(
        ContaContabilId contaOrigem,
        ContaContabilId contaDestino,
        Natureza naturezaDestino) {

    public ParametroEncerramentoDdr {
        Objects.requireNonNull(contaOrigem, "contaOrigem");
        Objects.requireNonNull(contaDestino, "contaDestino");
        Objects.requireNonNull(naturezaDestino, "naturezaDestino");
    }
}
