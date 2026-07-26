package br.contabil.execucao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.contabil.execucao.domain.Beneficiario;
import br.contabil.execucao.domain.CredorId;
import br.contabil.execucao.domain.DocumentoSuporte;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.NaturezaPagamento;
import br.contabil.execucao.domain.Pagamento;
import br.contabil.execucao.domain.PagamentoId;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.entrega.ServicoEntrega;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.IdEntrega;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.MensagemEntrega;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.Audiencia;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.CampoSensivel;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.Categoria;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicadorTransparenciaExecucaoTest {

    @Mock
    private ServicoEntrega entrega;

    @Mock
    private ServicoMascaramento mascaramento;

    @Mock
    private SinalizacaoSlaTransparenciaPort sinalizacaoSla;

    private PublicadorTransparenciaExecucao publicador;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final Sessao sessao = new Sessao(
            UUID.randomUUID(),
            new Cpf("12345678901"),
            enteId,
            Optional.empty(),
            true,
            Instant.parse("2030-01-01T00:00:00Z"));
    private final IdEntrega entregaId = new IdEntrega(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        Clock sextaFeira = Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC);
        publicador = new PublicadorTransparenciaExecucao(entrega, mascaramento, sinalizacaoSla, sextaFeira);
        when(entrega.enqueue(any(MensagemEntrega.class), any(ChaveIdempotencia.class))).thenReturn(entregaId);
    }

    @Test
    @DisplayName("empenho é enfileirado para transparência com chave idempotente")
    void publicaEmpenhoNoOutbox() {
        EmpenhoId empenhoId = EmpenhoId.novo();
        Empenho empenho = Empenho.registrar(
                empenhoId,
                enteId,
                7L,
                2026,
                TipoEmpenho.ORDINARIO,
                DotacaoId.novo(),
                CredorId.novo(),
                UnidadeGestoraId.novo(),
                null,
                Dinheiro.de("1500.00"),
                LocalDate.of(2026, 7, 20),
                "04.122.0001.2001",
                "0100000000",
                "empenho de material",
                UUID.randomUUID());

        publicador.publicar(empenho, sessao);

        ArgumentCaptor<MensagemEntrega> mensagem = ArgumentCaptor.forClass(MensagemEntrega.class);
        ArgumentCaptor<ChaveIdempotencia> chave = ArgumentCaptor.forClass(ChaveIdempotencia.class);
        verify(entrega).enqueue(mensagem.capture(), chave.capture());
        assertThat(mensagem.getValue().destino()).isEqualTo("transparencia");
        assertThat(mensagem.getValue().tipo()).isEqualTo("execucao.empenho.registrado.v1");
        assertThat(mensagem.getValue().conteudo())
                .contains("\"evento\":\"empenho\"")
                .contains("\"empenhoId\":\"" + empenhoId.valor() + "\"")
                .contains("\"numeroSequencial\":\"7\"")
                .contains("\"publicarAte\":\"2026-07-27T23:59:59.999999999Z\"");
        assertThat(chave.getValue().valor())
                .isEqualTo("transparencia:execucao:empenho:%s:%s".formatted(enteId.valor(), empenhoId.valor()));
    }

    @Test
    @DisplayName("liquidação é enfileirada para transparência com chave idempotente e SLA de 1o dia útil")
    void publicaLiquidacaoNoOutboxComSla() {
        LiquidacaoId liquidacaoId = LiquidacaoId.novo();
        Liquidacao liquidacao = Liquidacao.registrar(
                liquidacaoId,
                enteId,
                EmpenhoId.novo(),
                LocalDate.of(2026, 7, 20),
                Dinheiro.de("300.00"),
                List.of(DocumentoSuporte.de("NF", "NF-10", LocalDate.of(2026, 7, 19))),
                "liquidacao de material",
                UUID.randomUUID());

        publicador.publicar(liquidacao, sessao);

        ArgumentCaptor<MensagemEntrega> mensagem = ArgumentCaptor.forClass(MensagemEntrega.class);
        ArgumentCaptor<ChaveIdempotencia> chave = ArgumentCaptor.forClass(ChaveIdempotencia.class);
        verify(entrega).enqueue(mensagem.capture(), chave.capture());
        assertThat(mensagem.getValue().destino()).isEqualTo("transparencia");
        assertThat(mensagem.getValue().tipo()).isEqualTo("execucao.liquidacao.registrada.v1");
        assertThat(mensagem.getValue().conteudo())
                .contains("\"evento\":\"liquidacao\"")
                .contains("\"liquidacaoId\":\"" + liquidacaoId.valor() + "\"")
                .contains("\"publicarAte\":\"2026-07-27T23:59:59.999999999Z\"");
        assertThat(chave.getValue().valor())
                .isEqualTo("transparencia:execucao:liquidacao:%s:%s".formatted(enteId.valor(), liquidacaoId.valor()));

        ArgumentCaptor<SinalizacaoSlaTransparenciaPort.SinalPublicacao> sinal =
                ArgumentCaptor.forClass(SinalizacaoSlaTransparenciaPort.SinalPublicacao.class);
        verify(sinalizacaoSla).registrar(sinal.capture());
        assertThat(sinal.getValue().resultado())
                .isEqualTo(SinalizacaoSlaTransparenciaPort.ResultadoSla.DENTRO_DO_PRAZO);
        assertThat(sinal.getValue().publicarAte()).isEqualTo(Instant.parse("2026-07-27T23:59:59.999999999Z"));
    }

    @Test
    @DisplayName("pagamento publica CPF mascarado e suprime ordem bancária do payload público")
    void publicaPagamentoComPiiMascarada() {
        when(mascaramento.mascarar(any(CampoSensivel.class), any(), eq(Audiencia.PORTAL_PUBLICO)))
                .thenReturn("***.222.***-**");
        PagamentoId pagamentoId = PagamentoId.novo();
        Pagamento pagamento = Pagamento.registrar(
                pagamentoId,
                enteId,
                LiquidacaoId.novo(),
                LocalDate.of(2026, 7, 20),
                Dinheiro.de("300.00"),
                NaturezaPagamento.ORCAMENTARIO,
                Optional.of(new Beneficiario("Fornecedor Individual", "11122233344")),
                Optional.of("OB-10"),
                "pagamento de servico",
                UUID.randomUUID());

        publicador.publicar(pagamento, sessao);

        ArgumentCaptor<MensagemEntrega> mensagem = ArgumentCaptor.forClass(MensagemEntrega.class);
        verify(entrega).enqueue(mensagem.capture(), any(ChaveIdempotencia.class));
        assertThat(mensagem.getValue().tipo()).isEqualTo("execucao.pagamento.registrado.v1");
        assertThat(mensagem.getValue().conteudo())
                .contains("\"evento\":\"pagamento\"")
                .contains("\"pagamentoId\":\"" + pagamentoId.valor() + "\"")
                .contains("\"documento\":\"***.222.***-**\"")
                .doesNotContain("11122233344")
                .doesNotContain("OB-10");

        ArgumentCaptor<CampoSensivel> campo = ArgumentCaptor.forClass(CampoSensivel.class);
        verify(mascaramento).mascarar(campo.capture(), any(), eq(Audiencia.PORTAL_PUBLICO));
        assertThat(campo.getValue().categoria()).isEqualTo(Categoria.CPF);
    }
}
