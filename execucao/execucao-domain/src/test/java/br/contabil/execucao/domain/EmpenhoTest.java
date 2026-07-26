package br.contabil.execucao.domain;

import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;

class EmpenhoTest {

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final DotacaoId dotacaoId = DotacaoId.novo();
    private final CredorId credorId = CredorId.novo();
    private final UnidadeGestoraId unidadeGestoraId = UnidadeGestoraId.novo();
    private final Cpf autor = new Cpf("12345678901");

    @Test
    @DisplayName("registra empenho ordinário com fato contábil associado")
    void registraEmpenhoValido() {
        UUID fatoContabilId = UUID.randomUUID();

        Empenho empenho = Empenho.registrar(
                EmpenhoId.novo(),
                enteId,
                1L,
                2026,
                TipoEmpenho.ORDINARIO,
                dotacaoId,
                credorId,
                unidadeGestoraId,
                null,
                Dinheiro.de("1000.00"),
                LocalDate.of(2026, Month.JULY, 20),
                "04.122.0001.2001",
                "0100000000",
                "empenho de material de expediente",
                fatoContabilId,
                autor);

        assertThat(empenho.dotacaoId()).isEqualTo(dotacaoId);
        assertThat(empenho.valor()).isEqualTo(Dinheiro.de("1000.00"));
        assertThat(empenho.tipo()).isEqualTo(TipoEmpenho.ORDINARIO);
        assertThat(empenho.contratoId()).isNull();
        assertThat(empenho.fatoContabilId()).isEqualTo(fatoContabilId);
        assertThat(empenho.autor()).isEqualTo(autor);
    }

    @Test
    @DisplayName("rejeita valor zero ou negativo antes de qualquer I/O")
    void rejeitaValorInvalido() {
        assertThatThrownBy(() -> Empenho.registrar(
                        EmpenhoId.novo(),
                        enteId,
                        1L,
                        2026,
                        TipoEmpenho.ORDINARIO,
                        dotacaoId,
                        credorId,
                        unidadeGestoraId,
                        null,
                        Dinheiro.zero(),
                        LocalDate.of(2026, 7, 20),
                        "04.122.0001.2001",
                        "0100000000",
                        "empenho inválido",
                        UUID.randomUUID(),
                        autor))
                .isInstanceOf(ExecucaoInvalidaException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    @DisplayName("rejeita histórico em branco")
    void rejeitaHistoricoEmBranco() {
        assertThatThrownBy(() -> Empenho.registrar(
                        EmpenhoId.novo(),
                        enteId,
                        1L,
                        2026,
                        TipoEmpenho.ORDINARIO,
                        dotacaoId,
                        credorId,
                        unidadeGestoraId,
                        null,
                        Dinheiro.de("100.00"),
                        LocalDate.of(2026, 7, 20),
                        "04.122.0001.2001",
                        "0100000000",
                        "   ",
                        UUID.randomUUID(),
                        autor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("saldo da dotação impede empenhar acima do crédito disponível (art. 59)")
    void saldoDotacaoBloqueiaExcesso() {
        SaldoDotacao saldo = new SaldoDotacao(dotacaoId, Dinheiro.de("1000.00"), Dinheiro.de("700.00"));

        assertThat(saldo.saldoDisponivel()).isEqualTo(Dinheiro.de("300.00"));
        assertThatThrownBy(() -> saldo.exigirSaldoParaComprometer(Dinheiro.de("300.01")))
                .isInstanceOf(SaldoInsuficienteException.class)
                .hasMessageContaining("empenho");
    }

    @Test
    @DisplayName("rejeita snapshot de saldo com valor comprometido acima do autorizado")
    void rejeitaSaldoDotacaoInconsistente() {
        assertThatThrownBy(() -> new SaldoDotacao(dotacaoId, Dinheiro.de("100.00"), Dinheiro.de("200.00")))
                .isInstanceOf(ExecucaoInvalidaException.class);
    }
}
