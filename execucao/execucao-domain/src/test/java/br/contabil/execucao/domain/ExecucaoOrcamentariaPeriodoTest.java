package br.contabil.execucao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

class ExecucaoOrcamentariaPeriodoTest {

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());

    @Test
    @DisplayName("saldoALiquidar/saldoAPagar são derivados dos totais acumulados até o mês")
    void derivaSaldos() {
        ExecucaoOrcamentariaPeriodo execucao = new ExecucaoOrcamentariaPeriodo(
                enteId, 2026, 6, Dinheiro.de("1000.00"), Dinheiro.de("700.00"), Dinheiro.de("300.00"));

        assertThat(execucao.saldoALiquidar()).isEqualTo(Dinheiro.de("300.00"));
        assertThat(execucao.saldoAPagar()).isEqualTo(Dinheiro.de("400.00"));
    }

    @Test
    @DisplayName("sem execução alguma no período, os totais e saldos são zero")
    void periodoSemExecucaoAlgumaEZero() {
        ExecucaoOrcamentariaPeriodo execucao =
                new ExecucaoOrcamentariaPeriodo(enteId, 2026, 1, Dinheiro.zero(), Dinheiro.zero(), Dinheiro.zero());

        assertThat(execucao.saldoALiquidar()).isEqualTo(Dinheiro.zero());
        assertThat(execucao.saldoAPagar()).isEqualTo(Dinheiro.zero());
    }

    @Test
    @DisplayName("mes fora de 1-12 é rejeitado — execução não usa o mês 13 de encerramento do razão")
    void rejeitaMesForaDoIntervalo() {
        assertThatThrownBy(() -> new ExecucaoOrcamentariaPeriodo(
                        enteId, 2026, 13, Dinheiro.zero(), Dinheiro.zero(), Dinheiro.zero()))
                .isInstanceOf(ExecucaoInvalidaException.class)
                .hasMessageContaining("mes");
    }

    @Test
    @DisplayName("validarMes é reaproveitável por quem monta LocalDate ANTES de construir o agregado")
    void validarMesRejeitaAntesDeConstruirLocalDate() {
        assertThatThrownBy(() -> ExecucaoOrcamentariaPeriodo.validarMes(0))
                .isInstanceOf(ExecucaoInvalidaException.class)
                .hasMessageContaining("mes");
        assertThatThrownBy(() -> ExecucaoOrcamentariaPeriodo.validarMes(13))
                .isInstanceOf(ExecucaoInvalidaException.class);
    }
}
