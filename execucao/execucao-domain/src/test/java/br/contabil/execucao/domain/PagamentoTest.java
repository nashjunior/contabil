package br.contabil.execucao.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

class PagamentoTest {

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final LiquidacaoId liquidacaoId = LiquidacaoId.novo();
    private final Beneficiario fornecedor = new Beneficiario("Fornecedor A", "12345678000199");

    @Test
    @DisplayName("registra pagamento orçamentário com beneficiário e fato contábil associado")
    void registraPagamentoValido() {
        ReferenciaFatoContabil fatoContabilId = new ReferenciaFatoContabil(UUID.randomUUID());

        Pagamento pagamento = Pagamento.registrar(
                PagamentoId.novo(),
                enteId,
                liquidacaoId,
                LocalDate.of(2026, 7, 20),
                Dinheiro.de("250.00"),
                NaturezaPagamento.ORCAMENTARIO,
                Optional.of(fornecedor),
                Optional.of("OB-10"),
                "pagamento parcial",
                fatoContabilId);

        assertThat(pagamento.liquidacaoId()).isEqualTo(liquidacaoId);
        assertThat(pagamento.beneficiario()).contains(fornecedor);
        assertThat(pagamento.fatoContabilId()).isEqualTo(fatoContabilId);
    }

    @Test
    @DisplayName("pagamento orçamentário exige beneficiário nominal")
    void rejeitaPagamentoOrcamentarioSemBeneficiario() {
        assertThatThrownBy(() -> Pagamento.registrar(
                        PagamentoId.novo(),
                        enteId,
                        liquidacaoId,
                        LocalDate.of(2026, 7, 20),
                        Dinheiro.de("250.00"),
                        NaturezaPagamento.ORCAMENTARIO,
                        Optional.empty(),
                        Optional.empty(),
                        "pagamento sem beneficiário",
                        new ReferenciaFatoContabil(UUID.randomUUID())))
                .isInstanceOf(ExecucaoInvalidaException.class)
                .hasMessageContaining("beneficiário");
    }

    @Test
    @DisplayName("folha consolidada permite pagamento sem beneficiário linha-a-linha")
    void permiteFolhaConsolidadaSemBeneficiario() {
        Pagamento pagamento = Pagamento.registrar(
                PagamentoId.novo(),
                enteId,
                liquidacaoId,
                LocalDate.of(2026, 7, 20),
                Dinheiro.de("250.00"),
                NaturezaPagamento.FOLHA_CONSOLIDADA,
                Optional.empty(),
                Optional.empty(),
                "pagamento consolidado de folha",
                new ReferenciaFatoContabil(UUID.randomUUID()));

        assertThat(pagamento.beneficiario()).isEmpty();
    }

    @Test
    @DisplayName("saldo da liquidação impede pagar acima do liquidado")
    void saldoLiquidacaoBloqueiaExcesso() {
        SaldoLiquidacao saldo = new SaldoLiquidacao(liquidacaoId, Dinheiro.de("1000.00"), Dinheiro.de("700.00"));

        assertThat(saldo.saldoAPagar()).isEqualTo(Dinheiro.de("300.00"));
        assertThatThrownBy(() -> saldo.exigirSaldoParaPagar(Dinheiro.de("300.01")))
                .isInstanceOf(SaldoInsuficienteException.class)
                .hasMessageContaining("pagamento");
    }
}
