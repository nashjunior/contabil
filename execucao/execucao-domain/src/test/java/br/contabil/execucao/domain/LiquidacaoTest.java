package br.contabil.execucao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LiquidacaoTest {

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final EmpenhoId empenhoId = EmpenhoId.novo();
    private final DocumentoSuporte notaFiscal = DocumentoSuporte.de("NF", "123", LocalDate.of(2026, 7, 10));
    private final Cpf autor = new Cpf("11111111111");
    private final Cpf autorEmpenho = new Cpf("22222222222");
    private final Cpf aprovador = new Cpf("33333333333");

    @Test
    @DisplayName("registra liquidação com documento de suporte e fato contábil associado, pendente de aprovação")
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
                fatoContabilId,
                autor);

        assertThat(liquidacao.empenhoId()).isEqualTo(empenhoId);
        assertThat(liquidacao.valor()).isEqualTo(Dinheiro.de("100.00"));
        assertThat(liquidacao.documentosSuporte()).containsExactly(notaFiscal);
        assertThat(liquidacao.fatoContabilId()).isEqualTo(fatoContabilId);
        assertThat(liquidacao.autor()).isEqualTo(autor);
        assertThat(liquidacao.statusAprovacao()).isEqualTo(StatusAprovacao.PENDENTE);
        assertThat(liquidacao.aprovador()).isEmpty();
        assertThat(liquidacao.motivoDevolucao()).isEmpty();
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
                        UUID.randomUUID(),
                        autor))
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
                        UUID.randomUUID(),
                        autor))
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

    @Test
    @DisplayName("ADR-0023: aprovar transiciona PENDENTE -> APROVADA registrando o aprovador")
    void aprovarTransicionaParaAprovada() {
        Liquidacao liquidacao = liquidacaoValida();

        Liquidacao aprovada = liquidacao.aprovar(aprovador, autorEmpenho);

        assertThat(aprovada.statusAprovacao()).isEqualTo(StatusAprovacao.APROVADA);
        assertThat(aprovada.aprovador()).contains(aprovador);
        assertThat(aprovada.motivoDevolucao()).isEmpty();
    }

    @Test
    @DisplayName("ADR-0023: devolver transiciona PENDENTE -> DEVOLVIDA registrando motivo")
    void devolverTransicionaParaDevolvidaComMotivo() {
        Liquidacao liquidacao = liquidacaoValida();

        Liquidacao devolvida = liquidacao.devolver(aprovador, autorEmpenho, "documento fiscal divergente");

        assertThat(devolvida.statusAprovacao()).isEqualTo(StatusAprovacao.DEVOLVIDA);
        assertThat(devolvida.aprovador()).contains(aprovador);
        assertThat(devolvida.motivoDevolucao()).contains("documento fiscal divergente");
    }

    @Test
    @DisplayName("devolver exige motivo — devolução sem motivo é recusada")
    void devolverExigeMotivo() {
        Liquidacao liquidacao = liquidacaoValida();

        assertThatThrownBy(() -> liquidacao.devolver(aprovador, autorEmpenho, "   "))
                .isInstanceOf(ExecucaoInvalidaException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    @DisplayName("Regra 9/ADR-0023: recusa auto-aprovação quando aprovador é o autor da liquidação")
    void recusaAutoAprovacaoContraAutorDaLiquidacao() {
        Liquidacao liquidacao = liquidacaoValida();

        assertThatThrownBy(() -> liquidacao.aprovar(autor, autorEmpenho))
                .isInstanceOf(AutoAprovacaoNaoPermitidaException.class);
    }

    @Test
    @DisplayName("Regra 9/ADR-0023: recusa auto-aprovação quando aprovador é o autor do empenho da cadeia")
    void recusaAutoAprovacaoContraAutorDoEmpenho() {
        Liquidacao liquidacao = liquidacaoValida();

        assertThatThrownBy(() -> liquidacao.aprovar(autorEmpenho, autorEmpenho))
                .isInstanceOf(AutoAprovacaoNaoPermitidaException.class);
    }

    @Test
    @DisplayName("estado forte: liquidação já decidida não pode ser aprovada/devolvida de novo")
    void rejeitaDecisaoSobreLiquidacaoJaDecidida() {
        Liquidacao aprovada = liquidacaoValida().aprovar(aprovador, autorEmpenho);

        assertThatThrownBy(() -> aprovada.devolver(aprovador, autorEmpenho, "motivo qualquer"))
                .isInstanceOf(ExecucaoInvalidaException.class)
                .hasMessageContaining("já foi decidida");
    }

    private Liquidacao liquidacaoValida() {
        return Liquidacao.registrar(
                LiquidacaoId.novo(),
                enteId,
                empenhoId,
                LocalDate.of(2026, 7, 15),
                Dinheiro.de("100.00"),
                List.of(notaFiscal),
                "liquidação da NF 123",
                UUID.randomUUID(),
                autor);
    }
}
