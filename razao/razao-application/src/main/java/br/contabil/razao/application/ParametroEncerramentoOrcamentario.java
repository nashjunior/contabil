package br.contabil.razao.application;

import java.util.Objects;

import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.Natureza;

/**
 * Parâmetro de encerramento de uma conta de controle orçamentário PCASP (classes 5/6):
 * define a conta de controle a zerar (execução — classe 6) e a conta de contrapartida
 * (orçamento aprovado — classe 5) que recebe o lançamento de encerramento (RAZ-258,
 * IPC 03/STN rev. 2017 itens 35-63, revalidado na RAZ-232, docs/15-fechamento-contabil.md
 * §Preciso item 3). Não há conta de "resultado orçamentário" — cada par só transfere o
 * mesmo valor entre as duas contas.
 *
 * <p>Os códigos PCASP concretos são {@code [REVALIDAR]} no MCASP edição vigente
 * antes de configurar em produção. Exemplos (não normativos):
 * <ul>
 *   <li>Execução da despesa: controle 6.2.2.1.1/.3/.4/.5 (crédito disponível/empenhado a
 *       liquidar/liquidado a pagar/pago), contrapartida 5.2.2.1.1 (Crédito Inicial),
 *       natureza contrapartida = DEBITO.
 *   <li>Execução da receita: controle 6.2.1.x (receita realizada), contrapartida
 *       5.2.1.1.1.00.00 (Previsão Inicial da Receita Bruta), natureza contrapartida = CREDITO.
 * </ul>
 *
 * @param contaControle          conta de controle (classe 6) cujo saldo será zerado.
 * @param contaContrapartida     conta do orçamento aprovado (classe 5) que recebe a contrapartida.
 * @param naturezaContrapartida  natureza do lançamento na contrapartida (o controle recebe a inversa).
 */
public record ParametroEncerramentoOrcamentario(
        ContaContabilId contaControle,
        ContaContabilId contaContrapartida,
        Natureza naturezaContrapartida) {

    public ParametroEncerramentoOrcamentario {
        Objects.requireNonNull(contaControle, "contaControle");
        Objects.requireNonNull(contaContrapartida, "contaContrapartida");
        Objects.requireNonNull(naturezaContrapartida, "naturezaContrapartida");
    }
}
