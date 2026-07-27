package br.contabil.consulta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.contabil.execucao.application.ConsultarDotacoes;
import br.contabil.execucao.application.ConsultarEmpenhoPorId;
import br.contabil.execucao.application.ConsultarEmpenhosRegistrados;
import br.contabil.execucao.application.ConsultarExecucaoOrcamentaria;
import br.contabil.execucao.application.ConsultarFilaAprovacao;
import br.contabil.execucao.application.ConsultarLiquidacoesRegistradas;
import br.contabil.execucao.application.ConsultarPagamentosRegistrados;
import br.contabil.execucao.application.ConsultarTrilhaLiquidacao;
import br.contabil.execucao.domain.CredorId;
import br.contabil.execucao.domain.DocumentoAssinadoEmpenho;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.EmpenhoNaoEncontradoException;
import br.contabil.execucao.domain.ExecucaoOrcamentariaPeriodo;
import br.contabil.execucao.domain.FiltroFilaAprovacao;
import br.contabil.execucao.domain.ItemDotacaoComSaldo;
import br.contabil.execucao.domain.ItemEmpenhoRegistrado;
import br.contabil.execucao.domain.ItemFilaAprovacao;
import br.contabil.execucao.domain.ItemLiquidacaoRegistrada;
import br.contabil.execucao.domain.ItemPagamentoRegistrado;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.NaturezaPagamento;
import br.contabil.execucao.domain.PagamentoId;
import br.contabil.execucao.domain.PaginaDotacoes;
import br.contabil.execucao.domain.PaginaEmpenhosRegistrados;
import br.contabil.execucao.domain.PaginaFilaAprovacao;
import br.contabil.execucao.domain.PaginaLiquidacoesRegistradas;
import br.contabil.execucao.domain.PaginaPagamentosRegistrados;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.StatusAprovacao;
import br.contabil.execucao.domain.StatusEmpenho;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.TrilhaLiquidacao;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelAssinatura;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class ExecucaoConsultaControllerTest {

    @Mock
    private ConsultarExecucaoOrcamentaria consultarExecucaoOrcamentaria;

    @Mock
    private ConsultarFilaAprovacao consultarFilaAprovacao;

    @Mock
    private ConsultarTrilhaLiquidacao consultarTrilhaLiquidacao;

    @Mock
    private ConsultarEmpenhosRegistrados consultarEmpenhosRegistrados;

    @Mock
    private ConsultarEmpenhoPorId consultarEmpenhoPorId;

    @Mock
    private ConsultarDotacoes consultarDotacoes;

    @Mock
    private ConsultarLiquidacoesRegistradas consultarLiquidacoesRegistradas;

    @Mock
    private ConsultarPagamentosRegistrados consultarPagamentosRegistrados;

    /** Mesma serialização da app: {@link DinheiroJacksonModule} emite Dinheiro como string decimal (§6.1). */
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new DinheiroJacksonModule())
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private ExecucaoConsultaController controller() {
        return new ExecucaoConsultaController(
                consultarExecucaoOrcamentaria,
                consultarFilaAprovacao,
                consultarTrilhaLiquidacao,
                consultarEmpenhosRegistrados,
                consultarEmpenhoPorId,
                consultarDotacoes,
                consultarLiquidacoesRegistradas,
                consultarPagamentosRegistrados);
    }

    private Sessao sessaoDe(TenantId ente) {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), ente, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void orcamentariaAdaptaPathEQueryEDevolveTotaisDerivadosComoStringDecimal() throws Exception {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        ExecucaoOrcamentariaPeriodo esperado = new ExecucaoOrcamentariaPeriodo(
                new TenantId(enteId), 2026, 6, Dinheiro.de("1000.00"), Dinheiro.de("600.00"), Dinheiro.de("400.00"));
        when(consultarExecucaoOrcamentaria.executar(sessao, new TenantId(enteId), 2026, 6)).thenReturn(esperado);

        ExecucaoOrcamentariaResponse resposta = controller().orcamentaria(enteId, 2026, 6, sessao);

        assertThat(resposta.exercicio()).isEqualTo(2026);
        assertThat(resposta.mes()).isEqualTo(6);
        assertThat(resposta.totalEmpenhado()).isEqualTo(Dinheiro.de("1000.00"));
        assertThat(resposta.totalLiquidado()).isEqualTo(Dinheiro.de("600.00"));
        assertThat(resposta.totalPago()).isEqualTo(Dinheiro.de("400.00"));
        assertThat(resposta.saldoALiquidar()).isEqualTo(Dinheiro.de("400.00"));
        assertThat(resposta.saldoAPagar()).isEqualTo(Dinheiro.de("200.00"));
        // §6.1/ADR-0030 §2: totais serializam como string decimal, nunca número JSON.
        String corpo = json.writeValueAsString(resposta);
        assertThat(corpo).contains("\"totalEmpenhado\":\"1000.00\"");
        assertThat(corpo).contains("\"saldoAPagar\":\"200.00\"");
    }

    @Test
    void orcamentariaPropagaErroDeNegocioSemMapearNoController() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        when(consultarExecucaoOrcamentaria.executar(sessao, new TenantId(enteId), 2026, 6))
                .thenThrow(new SemPermissaoException("sem_permissao"));

        assertThatThrownBy(() -> controller().orcamentaria(enteId, 2026, 6, sessao))
                .isInstanceOf(SemPermissaoException.class);
    }

    @Test
    void filaAdaptaQueryParamsParaFiltroEMapeiaEnvelopeComCursorEDinheiroString() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        LiquidacaoId liquidacaoId = LiquidacaoId.novo();
        EmpenhoId empenhoId = EmpenhoId.novo();
        CredorId credorId = CredorId.novo();
        ItemFilaAprovacao item = new ItemFilaAprovacao(
                liquidacaoId, empenhoId, 42L, 2026, credorId, Dinheiro.de("4200.00"), LocalDate.of(2026, 7, 16), StatusAprovacao.PENDENTE);
        when(consultarFilaAprovacao.executar(eq(sessao), eq(new TenantId(enteId)), any(), any(), any()))
                .thenReturn(new PaginaFilaAprovacao(List.of(item), Optional.of("cursor-xyz")));

        var resposta = controller()
                .fila(
                        enteId,
                        "pendente",
                        null,
                        50,
                        "1000",
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 31),
                        "100.00",
                        "9000.00",
                        sessao);

        ArgumentCaptor<FiltroFilaAprovacao> filtro = ArgumentCaptor.forClass(FiltroFilaAprovacao.class);
        ArgumentCaptor<Optional<Integer>> limite = ArgumentCaptor.forClass(Optional.class);
        verify(consultarFilaAprovacao)
                .executar(eq(sessao), eq(new TenantId(enteId)), filtro.capture(), limite.capture(), eq(Optional.empty()));
        assertThat(filtro.getValue().statusAprovacao()).isEqualTo(StatusAprovacao.PENDENTE);
        assertThat(filtro.getValue().fonte()).contains("1000");
        assertThat(filtro.getValue().dataInicio()).contains(LocalDate.of(2026, 7, 1));
        assertThat(filtro.getValue().valorMin()).contains(Dinheiro.de("100.00"));
        assertThat(filtro.getValue().valorMax()).contains(Dinheiro.de("9000.00"));
        assertThat(limite.getValue()).contains(50);

        assertThat(resposta.proximoCursor()).isEqualTo("cursor-xyz");
        assertThat(resposta.itens()).hasSize(1);
        assertThat(resposta.itens().get(0).id()).isEqualTo(liquidacaoId.valor());
        assertThat(resposta.itens().get(0).credorId()).isEqualTo(credorId.valor());
        assertThat(resposta.itens().get(0).valor()).isEqualTo("4200.00");
        assertThat(resposta.itens().get(0).numeroEmpenho()).isEqualTo(42L);
        assertThat(resposta.itens().get(0).statusAprovacao()).isEqualTo("pendente");
    }

    @Test
    void trilhaAdaptaPathEMapeiaEventosComAtorMascarado() {
        UUID enteId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        EventoAuditoria decidida = new EventoAuditoria(
                new TenantId(enteId),
                "execucao_pagamento_aprovacao_decidida",
                "***.456.***-**",
                "execucao:liquidacao:" + id,
                Instant.parse("2026-07-13T00:00:00Z"),
                Map.of("decisao", "APROVADA"));
        when(consultarTrilhaLiquidacao.executar(eq(sessao), eq(new TenantId(enteId)), eq(new LiquidacaoId(id))))
                .thenReturn(new TrilhaLiquidacao(new LiquidacaoId(id), List.of(decidida)));

        var resposta = controller().trilha(enteId, id, sessao);

        assertThat(resposta.liquidacaoId()).isEqualTo(id);
        assertThat(resposta.eventos()).hasSize(1);
        assertThat(resposta.eventos().get(0).tipo()).isEqualTo("execucao_pagamento_aprovacao_decidida");
        assertThat(resposta.eventos().get(0).ator()).isEqualTo("***.456.***-**");
        assertThat(resposta.eventos().get(0).detalhes()).containsEntry("decisao", "APROVADA");
    }

    @Test
    void filaPropagaErroDeNegocioSemMapearNoController() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        when(consultarFilaAprovacao.executar(any(), any(), any(), any(), any()))
                .thenThrow(new SemPermissaoException("sem_permissao"));

        assertThatThrownBy(() -> controller()
                        .fila(enteId, "pendente", null, null, null, null, null, null, null, sessao))
                .isInstanceOf(SemPermissaoException.class);
    }

    @Test
    void empenhosAdaptaQueryParamsEMapeiaEnvelopeComCursorEDinheiroString() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        EmpenhoId empenhoId = EmpenhoId.novo();
        CredorId credorId = CredorId.novo();
        ItemEmpenhoRegistrado item = new ItemEmpenhoRegistrado(
                empenhoId, 7L, 2026, TipoEmpenho.ORDINARIO, credorId, Dinheiro.de("12300.00"),
                LocalDate.of(2026, 7, 15), "empenho de teste", StatusEmpenho.REGISTRADO);
        when(consultarEmpenhosRegistrados.executar(eq(sessao), eq(new TenantId(enteId)), any(), any(), any(), any(), any()))
                .thenReturn(new PaginaEmpenhosRegistrados(List.of(item), Optional.of("cursor-emp")));

        var resposta = controller().empenhos(
                enteId, 2026, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, 50, sessao);

        verify(consultarEmpenhosRegistrados).executar(
                eq(sessao), eq(new TenantId(enteId)), eq(Optional.of(2026)), eq(Optional.of(LocalDate.of(2026, 1, 1))),
                eq(Optional.of(LocalDate.of(2026, 12, 31))), eq(Optional.of(50)), eq(Optional.empty()));

        assertThat(resposta.proximoCursor()).isEqualTo("cursor-emp");
        assertThat(resposta.itens()).hasSize(1);
        assertThat(resposta.itens().get(0).id()).isEqualTo(empenhoId.valor());
        assertThat(resposta.itens().get(0).credorId()).isEqualTo(credorId.valor());
        assertThat(resposta.itens().get(0).valor()).isEqualTo("12300.00");
        assertThat(resposta.itens().get(0).tipo()).isEqualTo("ordinario");
        assertThat(resposta.itens().get(0).status()).isEqualTo("registrado");
    }

    @Test
    void empenhosPropagaErroDeNegocioSemMapearNoController() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        when(consultarEmpenhosRegistrados.executar(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new SemPermissaoException("sem_permissao"));

        assertThatThrownBy(() -> controller().empenhos(enteId, null, null, null, null, null, sessao))
                .isInstanceOf(SemPermissaoException.class);
    }

    private Empenho empenhoRegistrado(UUID enteId, EmpenhoId empenhoId, CredorId credorId) {
        return Empenho.registrar(
                empenhoId,
                new TenantId(enteId),
                9L,
                2026,
                TipoEmpenho.ORDINARIO,
                DotacaoId.novo(),
                credorId,
                UnidadeGestoraId.novo(),
                null,
                Dinheiro.de("5000.00"),
                LocalDate.of(2026, 7, 20),
                "04.122.0001.2001",
                "0100000000",
                "empenho de teste RAZ-156",
                new ReferenciaFatoContabil(UUID.randomUUID()),
                new Cpf("11122233344"));
    }

    private URI uriDoEnte(UUID enteId, String sufixo) {
        return URI.create("s3://ged/%s/%s".formatted(enteId, sufixo));
    }

    @Test
    void empenhoPorIdAdaptaPathEDevolveDocumentoNuloQuandoRegistradoSemDocumento() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        EmpenhoId empenhoId = EmpenhoId.novo();
        CredorId credorId = CredorId.novo();
        Empenho empenho = empenhoRegistrado(enteId, empenhoId, credorId);
        when(consultarEmpenhoPorId.executar(eq(sessao), eq(new TenantId(enteId)), eq(empenhoId))).thenReturn(empenho);

        var resposta = controller().empenhoPorId(enteId, empenhoId.valor(), sessao);

        assertThat(resposta.id()).isEqualTo(empenhoId.valor());
        assertThat(resposta.credorId()).isEqualTo(credorId.valor());
        assertThat(resposta.valor()).isEqualTo("5000.00");
        assertThat(resposta.status()).isEqualTo("registrado");
        assertThat(resposta.documento().pendenteUri()).isNull();
        assertThat(resposta.documento().assinado()).isNull();
    }

    @Test
    void empenhoPorIdDevolvePendenteUriOpacoQuandoPendenteAssinaturaENuncaAS3Crua() throws Exception {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        EmpenhoId empenhoId = EmpenhoId.novo();
        URI uriPendente = uriDoEnte(enteId, "nota-empenho.pdf");
        Empenho empenho = empenhoRegistrado(enteId, empenhoId, CredorId.novo()).marcarPendenteAssinatura(uriPendente);
        when(consultarEmpenhoPorId.executar(eq(sessao), eq(new TenantId(enteId)), eq(empenhoId))).thenReturn(empenho);

        var resposta = controller().empenhoPorId(enteId, empenhoId.valor(), sessao);

        assertThat(resposta.status()).isEqualTo("pendente_assinatura");
        assertThat(resposta.documento().pendenteUri()).isNotNull().isNotEqualTo(uriPendente.toString());
        assertThat(resposta.documento().assinado()).isNull();

        String corpo = json.writeValueAsString(resposta);
        assertThat(corpo).doesNotContain("s3://");
    }

    @Test
    void empenhoPorIdDevolvePendenteUriOpacoQuandoAssinaturaRejeitada() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        EmpenhoId empenhoId = EmpenhoId.novo();
        URI uriPendente = uriDoEnte(enteId, "nota-empenho.pdf");
        Empenho empenho = empenhoRegistrado(enteId, empenhoId, CredorId.novo())
                .marcarPendenteAssinatura(uriPendente)
                .rejeitarAssinatura();
        when(consultarEmpenhoPorId.executar(eq(sessao), eq(new TenantId(enteId)), eq(empenhoId))).thenReturn(empenho);

        var resposta = controller().empenhoPorId(enteId, empenhoId.valor(), sessao);

        assertThat(resposta.status()).isEqualTo("assinatura_rejeitada");
        assertThat(resposta.documento().pendenteUri()).isNotNull();
        assertThat(resposta.documento().assinado()).isNull();
    }

    @Test
    void empenhoPorIdDevolveBlocoAssinadoComCpfMascaradoENuncaAS3CruaQuandoAssinado() throws Exception {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        EmpenhoId empenhoId = EmpenhoId.novo();
        URI uriPendente = uriDoEnte(enteId, "nota-empenho.pdf");
        URI uriAssinado = uriDoEnte(enteId, "nota-empenho-assinado.pdf");
        UUID idTransacao = UUID.randomUUID();
        DocumentoAssinadoEmpenho documentoAssinado = new DocumentoAssinadoEmpenho(
                uriAssinado, "hash-abc", "manifesto-xyz", idTransacao, NivelAssinatura.AVANCADA_GOVBR,
                new Cpf("98765432109"), Instant.parse("2026-07-26T12:00:00Z"));
        Empenho empenho = empenhoRegistrado(enteId, empenhoId, CredorId.novo())
                .marcarPendenteAssinatura(uriPendente)
                .assinar(documentoAssinado);
        when(consultarEmpenhoPorId.executar(eq(sessao), eq(new TenantId(enteId)), eq(empenhoId))).thenReturn(empenho);

        var resposta = controller().empenhoPorId(enteId, empenhoId.valor(), sessao);

        assertThat(resposta.status()).isEqualTo("assinado");
        // pendenteUri não vaza mesmo com o Optional do agregado ainda preenchido (nunca é limpo na transição).
        assertThat(resposta.documento().pendenteUri()).isNull();
        assertThat(resposta.documento().assinado()).isNotNull();
        assertThat(resposta.documento().assinado().hashSha256()).isEqualTo("hash-abc");
        assertThat(resposta.documento().assinado().idTransacao()).isEqualTo(idTransacao);
        assertThat(resposta.documento().assinado().nivel()).isEqualTo("AVANCADA_GOVBR");
        assertThat(resposta.documento().assinado().signatario()).isEqualTo("***.654.***-**");
        assertThat(resposta.documento().assinado().assinadoEm()).isEqualTo(Instant.parse("2026-07-26T12:00:00Z"));

        String corpo = json.writeValueAsString(resposta);
        assertThat(corpo).doesNotContain("s3://");
        assertThat(corpo).doesNotContain("98765432109");
    }

    @Test
    void empenhoPorIdPropagaErroDeNegocioSemMapearNoController() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        EmpenhoId empenhoId = EmpenhoId.novo();
        when(consultarEmpenhoPorId.executar(eq(sessao), eq(new TenantId(enteId)), eq(empenhoId)))
                .thenThrow(new EmpenhoNaoEncontradoException("empenho não encontrado"));

        assertThatThrownBy(() -> controller().empenhoPorId(enteId, empenhoId.valor(), sessao))
                .isInstanceOf(EmpenhoNaoEncontradoException.class);
    }

    @Test
    void dotacoesAdaptaQueryParamsEMapeiaSaldoInlineComoStringDecimal() throws Exception {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        DotacaoId dotacaoId = DotacaoId.novo();
        UnidadeGestoraId unidadeGestoraId = UnidadeGestoraId.novo();
        ItemDotacaoComSaldo item = new ItemDotacaoComSaldo(
                dotacaoId,
                2026,
                "12.361.0021.2044",
                "01 - Recursos ordinarios",
                unidadeGestoraId,
                Dinheiro.de("150000.00"),
                Dinheiro.de("21550.00"));
        when(consultarDotacoes.executar(eq(sessao), eq(new TenantId(enteId)), eq(2026), any(), any(), any()))
                .thenReturn(new PaginaDotacoes(List.of(item), Optional.of("cursor-dot")));

        var resposta = controller().dotacoes(enteId, 2026, "12.361", null, 50, sessao);

        verify(consultarDotacoes)
                .executar(
                        eq(sessao),
                        eq(new TenantId(enteId)),
                        eq(2026),
                        eq(Optional.of("12.361")),
                        eq(Optional.of(50)),
                        eq(Optional.empty()));
        assertThat(resposta.proximoCursor()).isEqualTo("cursor-dot");
        assertThat(resposta.itens()).hasSize(1);
        assertThat(resposta.itens().get(0).id()).isEqualTo(dotacaoId.valor());
        assertThat(resposta.itens().get(0).exercicio()).isEqualTo(2026);
        assertThat(resposta.itens().get(0).classificacaoOrcamentaria()).isEqualTo("12.361.0021.2044");
        assertThat(resposta.itens().get(0).unidadeGestoraId()).isEqualTo(unidadeGestoraId.valor());
        assertThat(resposta.itens().get(0).valorAutorizado()).isEqualTo("150000.00");
        assertThat(resposta.itens().get(0).valorComprometido()).isEqualTo("21550.00");
        assertThat(resposta.itens().get(0).saldoDisponivel()).isEqualTo("128450.00");

        String corpo = json.writeValueAsString(resposta);
        assertThat(corpo).contains("\"saldoDisponivel\":\"128450.00\"");
    }

    @Test
    void dotacoesPropagaErroDeNegocioSemMapearNoController() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        when(consultarDotacoes.executar(any(), any(), eq(2026), any(), any(), any()))
                .thenThrow(new SemPermissaoException("sem_permissao"));

        assertThatThrownBy(() -> controller().dotacoes(enteId, 2026, null, null, null, sessao))
                .isInstanceOf(SemPermissaoException.class);
    }

    @Test
    void liquidacoesRegistradasAdaptaQueryParamsEMapeiaEnvelopeSemSegregacaoDeAutor() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        LiquidacaoId liquidacaoId = LiquidacaoId.novo();
        EmpenhoId empenhoId = EmpenhoId.novo();
        ItemLiquidacaoRegistrada item = new ItemLiquidacaoRegistrada(
                liquidacaoId, empenhoId, Dinheiro.de("4200.00"), LocalDate.of(2026, 7, 16), "liquidacao de teste",
                StatusAprovacao.APROVADA);
        when(consultarLiquidacoesRegistradas.executar(eq(sessao), eq(new TenantId(enteId)), any(), any(), any(), any()))
                .thenReturn(new PaginaLiquidacoesRegistradas(List.of(item), Optional.empty()));

        var resposta = controller().liquidacoesRegistradas(
                enteId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "cursor-in", 20, sessao);

        verify(consultarLiquidacoesRegistradas).executar(
                eq(sessao), eq(new TenantId(enteId)), eq(Optional.of(LocalDate.of(2026, 7, 1))),
                eq(Optional.of(LocalDate.of(2026, 7, 31))), eq(Optional.of(20)), eq(Optional.of("cursor-in")));

        assertThat(resposta.proximoCursor()).isNull();
        assertThat(resposta.itens()).hasSize(1);
        assertThat(resposta.itens().get(0).id()).isEqualTo(liquidacaoId.valor());
        assertThat(resposta.itens().get(0).empenhoId()).isEqualTo(empenhoId.valor());
        assertThat(resposta.itens().get(0).valor()).isEqualTo("4200.00");
        assertThat(resposta.itens().get(0).statusAprovacao()).isEqualTo("aprovada");
    }

    @Test
    void liquidacoesRegistradasPropagaErroDeNegocioSemMapearNoController() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        when(consultarLiquidacoesRegistradas.executar(any(), any(), any(), any(), any(), any()))
                .thenThrow(new SemPermissaoException("sem_permissao"));

        assertThatThrownBy(() -> controller().liquidacoesRegistradas(enteId, null, null, null, null, sessao))
                .isInstanceOf(SemPermissaoException.class);
    }

    @Test
    void pagamentosAdaptaQueryParamsEMapeiaEnvelopeSemBeneficiario() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        PagamentoId pagamentoId = PagamentoId.novo();
        LiquidacaoId liquidacaoId = LiquidacaoId.novo();
        ItemPagamentoRegistrado item = new ItemPagamentoRegistrado(
                pagamentoId, liquidacaoId, Dinheiro.de("4200.00"), LocalDate.of(2026, 7, 18),
                NaturezaPagamento.ORCAMENTARIO, "pagamento de teste");
        when(consultarPagamentosRegistrados.executar(eq(sessao), eq(new TenantId(enteId)), any(), any(), any(), any()))
                .thenReturn(new PaginaPagamentosRegistrados(List.of(item), Optional.empty()));

        var resposta = controller().pagamentos(enteId, null, null, null, null, sessao);

        assertThat(resposta.itens()).hasSize(1);
        assertThat(resposta.itens().get(0).id()).isEqualTo(pagamentoId.valor());
        assertThat(resposta.itens().get(0).liquidacaoId()).isEqualTo(liquidacaoId.valor());
        assertThat(resposta.itens().get(0).valor()).isEqualTo("4200.00");
        assertThat(resposta.itens().get(0).natureza()).isEqualTo("orcamentario");
        // resumo de registro não vaza PII de beneficiário (04-lgpd) — o record não tem esse campo.
        assertThat(ExecucaoConsultaController.PagamentoRegistradoResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("beneficiario", "beneficiarioNome", "beneficiarioCpfCnpj");
    }

    @Test
    void pagamentosPropagaErroDeNegocioSemMapearNoController() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        when(consultarPagamentosRegistrados.executar(any(), any(), any(), any(), any(), any()))
                .thenThrow(new SemPermissaoException("sem_permissao"));

        assertThatThrownBy(() -> controller().pagamentos(enteId, null, null, null, null, sessao))
                .isInstanceOf(SemPermissaoException.class);
    }
}
