package br.contabil.razao.domain;

import java.util.List;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.Validacoes;

/**
 * Matriz de Saldos Contábeis (MSC, Portaria STN 642/2019) de um período/ente — read-model
 * DERIVADO dos lançamentos (ADR-0007), extensão de {@link Balancete} pela dimensão das
 * informações complementares (IC, ADR-0048/ADR-0056/ADR-0057): cada linha CAPTURADA
 * ({@link LinhaMatrizSaldosContabeis}) é conta PCASP último nível × IC de partida/fato;
 * as linhas DERIVADAS ({@link LinhaMatrizSaldosContabeisDerivada}) trazem {@code FP}/{@code DC}
 * por {@link FonteRecurso}. O agregado nativo do razão — não o long-format {@code TIPO_VALOR}
 * × {@code NATUREZA} × {@code VALOR} do SICONFI, que é montado por unpivot em
 * {@code prestacao-contas} (RAZ-251/ADR-0056), fora do ledger.
 *
 * <p>Mesmo princípio de {@link Balancete}: um período sem nenhum lançamento devolve uma
 * matriz de linhas vazias (não é erro).
 */
public record MatrizSaldosContabeis(
        TenantId enteId,
        int exercicio,
        int mes,
        List<LinhaMatrizSaldosContabeis> linhas,
        List<LinhaMatrizSaldosContabeisDerivada> linhasDerivadas) {

    public MatrizSaldosContabeis {
        Validacoes.exigirNaoNulo(enteId, "enteId");
        Validacoes.exigirNaoNulo(linhas, "linhas");
        Validacoes.exigirNaoNulo(linhasDerivadas, "linhasDerivadas");
        linhas = List.copyOf(linhas);
        linhasDerivadas = List.copyOf(linhasDerivadas);
    }

    public Dinheiro totalMovimentoDebito() {
        return linhas.stream()
                .map(LinhaMatrizSaldosContabeis::movimentoDebito)
                .reduce(Dinheiro.zero(), Dinheiro::somar);
    }

    public Dinheiro totalMovimentoCredito() {
        return linhas.stream()
                .map(LinhaMatrizSaldosContabeis::movimentoCredito)
                .reduce(Dinheiro.zero(), Dinheiro::somar);
    }

    /**
     * Σ movimento a débito = Σ movimento a crédito das linhas CAPTURADAS — a matriz
     * "fecha". As {@link #linhasDerivadas()} (FP/DC) ficam de fora: são rótulo sobre um
     * saldo já contado numa linha capturada da DDR, não um fato adicional a somar.
     */
    public boolean confere() {
        return totalMovimentoDebito().equals(totalMovimentoCredito());
    }
}
