package br.contabil.razao.domain;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

class BalanceteTest {

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());

    private LinhaBalancete linha(String anterior, String debito, String credito, String atual) {
        return new LinhaBalancete(
                ContaContabilId.novo(),
                "1.1.1",
                "Caixa",
                Dinheiro.de(anterior),
                Dinheiro.de(debito),
                Dinheiro.de(credito),
                Dinheiro.de(atual));
    }

    @Test
    @DisplayName("confere() quando Σ movimento débito = Σ movimento crédito do período")
    void confereQuandoMovimentosBatem() {
        Balancete balancete = new Balancete(
                enteId,
                2026,
                1,
                List.of(linha("0.00", "1000.00", "600.00", "400.00"), linha("0.00", "600.00", "1000.00", "-400.00")));

        assertThat(balancete.totalMovimentoDebito()).isEqualTo(Dinheiro.de("1600.00"));
        assertThat(balancete.totalMovimentoCredito()).isEqualTo(Dinheiro.de("1600.00"));
        assertThat(balancete.confere()).isTrue();
    }

    @Test
    @DisplayName("não confere quando Σ movimento débito ≠ Σ movimento crédito (encerramento deve bloquear)")
    void naoConfereQuandoMovimentosDivergem() {
        Balancete balancete = new Balancete(enteId, 2026, 1, List.of(linha("0.00", "1000.00", "600.00", "400.00")));

        assertThat(balancete.confere()).isFalse();
    }

    @Test
    @DisplayName("período sem nenhum lançamento é um balancete de linhas vazias, não um erro")
    void periodoSemLancamentosConfereTrivialmente() {
        Balancete balancete = new Balancete(enteId, 2026, 1, List.of());

        assertThat(balancete.linhas()).isEmpty();
        assertThat(balancete.confere()).isTrue();
    }
}
