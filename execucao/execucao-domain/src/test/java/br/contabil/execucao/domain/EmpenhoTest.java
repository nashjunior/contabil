package br.contabil.execucao.domain;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelAssinatura;
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
        assertThat(empenho.status()).isEqualTo(StatusEmpenho.REGISTRADO);
        assertThat(empenho.documentoPendenteUri()).isEmpty();
        assertThat(empenho.documentoAssinado()).isEmpty();
    }

    @Test
    @DisplayName("marcarPendenteAssinatura transiciona REGISTRADO -> PENDENTE_ASSINATURA com a referência do PDF")
    void marcarPendenteAssinaturaTransicionaEArmazenaReferencia() {
        Empenho registrado = empenhoRegistrado();
        URI documentoPendenteUri = URI.create("s3://ged/%s/execucao/empenho/%s/nota-empenho.pdf"
                .formatted(enteId.valor(), registrado.id().valor()));

        Empenho pendente = registrado.marcarPendenteAssinatura(documentoPendenteUri);

        assertThat(pendente.status()).isEqualTo(StatusEmpenho.PENDENTE_ASSINATURA);
        assertThat(pendente.documentoPendenteUri()).contains(documentoPendenteUri);
        assertThat(pendente.documentoAssinado()).isEmpty();
    }

    @Test
    @DisplayName("marcarPendenteAssinatura falha quando o empenho não está mais REGISTRADO")
    void marcarPendenteAssinaturaFalhaForaDeRegistrado() {
        Empenho pendente = empenhoPendenteAssinatura();

        assertThatThrownBy(() -> pendente.marcarPendenteAssinatura(pendente.documentoPendenteUri().orElseThrow()))
                .isInstanceOf(EmpenhoAssinaturaConflitanteException.class);
    }

    @Test
    @DisplayName("assinar transiciona PENDENTE_ASSINATURA -> ASSINADO e persiste o documento assinado")
    void assinarTransicionaParaAssinado() {
        Empenho pendente = empenhoPendenteAssinatura();
        DocumentoAssinadoEmpenho documentoAssinado = documentoAssinado();

        Empenho assinado = pendente.assinar(documentoAssinado);

        assertThat(assinado.status()).isEqualTo(StatusEmpenho.ASSINADO);
        assertThat(assinado.documentoAssinado()).contains(documentoAssinado);
    }

    @Test
    @DisplayName("assinar também aceita retrabalho a partir de ASSINATURA_REJEITADA (R2)")
    void assinarAceitaRetrabalhoDeRejeitada() {
        Empenho rejeitado = empenhoPendenteAssinatura().rejeitarAssinatura();

        Empenho assinado = rejeitado.assinar(documentoAssinado());

        assertThat(assinado.status()).isEqualTo(StatusEmpenho.ASSINADO);
    }

    @Test
    @DisplayName("assinar falha quando o empenho já está REGISTRADO (documento ainda não gerado)")
    void assinarFalhaAntesDePendenteAssinatura() {
        Empenho registrado = empenhoRegistrado();

        assertThatThrownBy(() -> registrado.assinar(documentoAssinado()))
                .isInstanceOf(EmpenhoAssinaturaConflitanteException.class);
    }

    @Test
    @DisplayName("assinar falha quando o empenho já está ASSINADO — nunca uma segunda assinatura (R3)")
    void assinarFalhaQuandoJaAssinado() {
        Empenho assinado = empenhoPendenteAssinatura().assinar(documentoAssinado());

        assertThatThrownBy(() -> assinado.assinar(documentoAssinado()))
                .isInstanceOf(EmpenhoAssinaturaConflitanteException.class);
    }

    @Test
    @DisplayName("rejeitarAssinatura transiciona PENDENTE_ASSINATURA -> ASSINATURA_REJEITADA sem tocar o razão")
    void rejeitarAssinaturaTransicionaParaRetrabalho() {
        Empenho pendente = empenhoPendenteAssinatura();

        Empenho rejeitado = pendente.rejeitarAssinatura();

        assertThat(rejeitado.status()).isEqualTo(StatusEmpenho.ASSINATURA_REJEITADA);
        assertThat(rejeitado.fatoContabilId()).isEqualTo(pendente.fatoContabilId());
    }

    private Empenho empenhoRegistrado() {
        return Empenho.registrar(
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
                UUID.randomUUID(),
                autor);
    }

    private Empenho empenhoPendenteAssinatura() {
        Empenho registrado = empenhoRegistrado();
        URI documentoPendenteUri = URI.create("s3://ged/%s/execucao/empenho/%s/nota-empenho.pdf"
                .formatted(enteId.valor(), registrado.id().valor()));
        return registrado.marcarPendenteAssinatura(documentoPendenteUri);
    }

    private DocumentoAssinadoEmpenho documentoAssinado() {
        return new DocumentoAssinadoEmpenho(
                URI.create("s3://ged/%s/execucao/empenho/x/nota-empenho-assinado.pdf".formatted(enteId.valor())),
                "hash-sha256",
                "manifesto",
                UUID.randomUUID(),
                NivelAssinatura.AVANCADA_GOVBR,
                autor,
                Instant.parse("2026-07-26T10:00:00Z"));
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
