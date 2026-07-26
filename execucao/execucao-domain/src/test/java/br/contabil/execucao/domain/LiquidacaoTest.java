package br.contabil.execucao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LiquidacaoTest {

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final EmpenhoId empenhoId = EmpenhoId.novo();
    private final DocumentoSuporte notaFiscal = DocumentoSuporte.de("NF", "123", LocalDate.of(2026, 7, 10));

    @Test
    @DisplayName("registra liquidação com documento de suporte e fato contábil associado")
    void registraLiquidacaoValida() {
        UUID fatoContabilId = UUID.randomUUID();

        Liquidacao liquidacao = Liquidacao.registrar(
                LiquidacaoId.novo(),
                enteId,
                empenhoId,
                LocalDate.of(2026, 7, 15),
                Dinheiro.de("100.00"),
                List.of(notaFiscal),
                "liquidação da NF 123",
                fatoContabilId);

        assertThat(liquidacao.empenhoId()).isEqualTo(empenhoId);
        assertThat(liquidacao.valor()).isEqualTo(Dinheiro.de("100.00"));
        assertThat(liquidacao.documentosSuporte()).containsExactly(notaFiscal);
        assertThat(liquidacao.fatoContabilId()).isEqualTo(fatoContabilId);
    }

    @Test
    @DisplayName("Lei 4.320 art. 63: liquidação sem documento de suporte é inválida")
    void rejeitaSemDocumentoSuporte() {
        assertThatThrownBy(() -> Liquidacao.registrar(
                        LiquidacaoId.novo(),
                        enteId,
                        empenhoId,
                        LocalDate.of(2026, 7, 15),
                        Dinheiro.de("100.00"),
                        List.of(),
                        "liquidação sem documento",
                        UUID.randomUUID()))
                .isInstanceOf(ExecucaoInvalidaException.class)
                .hasMessageContaining("documento de suporte");
    }

    @Test
    @DisplayName("valor da liquidação precisa ser positivo")
    void rejeitaValorZero() {
        assertThatThrownBy(() -> Liquidacao.registrar(
                        LiquidacaoId.novo(),
                        enteId,
                        empenhoId,
                        LocalDate.of(2026, 7, 15),
                        Dinheiro.zero(),
                        List.of(notaFiscal),
                        "liquidação zero",
                        UUID.randomUUID()))
                .isInstanceOf(ExecucaoInvalidaException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    @DisplayName("saldo do empenho impede liquidar acima do empenhado")
    void saldoEmpenhoBloqueiaExcesso() {
        SaldoEmpenho saldo = new SaldoEmpenho(empenhoId, Dinheiro.de("1000.00"), Dinheiro.de("700.00"));

        assertThat(saldo.saldoALiquidar()).isEqualTo(Dinheiro.de("300.00"));
        assertThatThrownBy(() -> saldo.exigirSaldoParaLiquidar(Dinheiro.de("300.01")))
                .isInstanceOf(SaldoInsuficienteException.class)
                .hasMessageContaining("liquidação");
    }
}
