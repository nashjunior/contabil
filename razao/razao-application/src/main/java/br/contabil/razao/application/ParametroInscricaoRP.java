package br.contabil.razao.application;

import java.util.Objects;

import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.Natureza;

/**
 * Parâmetro de inscrição de uma linha de Restos a Pagar: define a conta de origem
 * (empenhos pendentes), a conta de destino (RP inscrito) e a natureza do lançamento
 * na conta de destino.
 *
 * <p>Os códigos PCASP concretos são {@code [REVALIDAR]} no MCASP edição vigente
 * antes de configurar em produção. Exemplos (não normativos):
 * <ul>
 *   <li>RPNP: origem 6.2.2.1.2.x (empenhos a liquidar), destino 6.2.2.3.x (RPNP inscritos),
 *       natureza destino = CREDITO.
 *   <li>RPP:  origem 6.2.2.1.3.x (empenhos liquidados a pagar), destino 6.2.2.2.x (RPP inscritos),
 *       natureza destino = CREDITO.
 * </ul>
 *
 * @param contaOrigem    conta cujos saldos por fonte serão transferidos — ex.: 6.2.2.1.2.x.
 * @param contaDestino   conta de RP que receberá a inscrição — ex.: 6.2.2.3.x.
 * @param naturezaDestino natureza do lançamento na conta de destino (a origem recebe a inversa).
 */
public record ParametroInscricaoRP(
        ContaContabilId contaOrigem,
        ContaContabilId contaDestino,
        Natureza naturezaDestino) {

    public ParametroInscricaoRP {
        Objects.requireNonNull(contaOrigem, "contaOrigem");
        Objects.requireNonNull(contaDestino, "contaDestino");
        Objects.requireNonNull(naturezaDestino, "naturezaDestino");
    }
}
