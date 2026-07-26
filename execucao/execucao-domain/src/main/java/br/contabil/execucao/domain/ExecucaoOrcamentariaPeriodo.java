package br.contabil.execucao.domain;

import java.util.Objects;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.ErroContrato;
import br.contabil.plataforma.domain.TenantId;

/**
 * Execução orçamentária da despesa de um ente, acumulada do início do
 * {@code exercicio} até o {@code mes} informado (mesma leitura "até o bimestre/mês"
 * do RREO — 07-roadmap.md classifica RREO como obrigatório no F1). Totais são
 * DERIVADOS de {@code empenho}/{@code liquidacao}/{@code pagamento}
 * (execucao-orcamentaria-despesa.md), nunca uma tabela de acumulado mutada.
 *
 * <p>Diferente de {@link SaldoEmpenho}/{@link SaldoLiquidacao} (saldo de UM
 * agregado, usado para travar o próximo estágio), este é um AGREGADO DE
 * RELATÓRIO sobre muitos empenhos/liquidações/pagamentos do período — não há
 * garantia física de {@code totalLiquidado <= totalEmpenhado} no schema atual
 * (mesmo gap já registrado em {@code GoldSetConformidadeContabilTest}), por isso
 * não valida essa relação como invariante, só a expõe como leitura derivada.
 */
public record ExecucaoOrcamentariaPeriodo(
        TenantId enteId,
        int exercicio,
        int mes,
        Dinheiro totalEmpenhado,
        Dinheiro totalLiquidado,
        Dinheiro totalPago) {

    public ExecucaoOrcamentariaPeriodo {
        Objects.requireNonNull(enteId, "enteId não pode ser nulo");
        Objects.requireNonNull(totalEmpenhado, "totalEmpenhado não pode ser nulo");
        Objects.requireNonNull(totalLiquidado, "totalLiquidado não pode ser nulo");
        Objects.requireNonNull(totalPago, "totalPago não pode ser nulo");
        validarMes(mes);
    }

    /**
     * Valida {@code mes} ANTES de qualquer uso em {@link java.time.LocalDate#of} — chamar
     * {@code LocalDate.of} direto com um mês fora de 1-12 estoura
     * {@link java.time.DateTimeException} (não é {@link ErroContrato}), escapando do
     * tratamento de erro de domínio que o resto do sistema espera.
     */
    public static void validarMes(int mes) {
        if (mes < 1 || mes > 12) {
            throw new ExecucaoInvalidaException("periodo_invalido", "mes deve estar entre 1 e 12: " + mes);
        }
    }

    /** Empenhado no exercício até o mês, ainda sem liquidação (RP a liquidar). */
    public Dinheiro saldoALiquidar() {
        return totalEmpenhado.subtrair(totalLiquidado);
    }

    /** Liquidado no exercício até o mês, ainda sem pagamento (restos a pagar processados). */
    public Dinheiro saldoAPagar() {
        return totalLiquidado.subtrair(totalPago);
    }
}
