package br.contabil.execucao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.execucao.domain.Beneficiario;
import br.contabil.execucao.domain.DocumentoSuporte;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.NaturezaPagamento;
import br.contabil.execucao.domain.Pagamento;
import br.contabil.execucao.domain.PagamentoId;
import br.contabil.execucao.domain.PagamentoNaoAprovadoException;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.SaldoInsuficienteException;
import br.contabil.execucao.domain.SaldoLiquidacao;
import br.contabil.execucao.domain.StatusAprovacao;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort.SolicitacaoEscrituracaoPagamento;
import br.contabil.execucao.domain.repository.IdempotenciaPagamentoRepository;
import br.contabil.execucao.domain.repository.LiquidacaoRepository;
import br.contabil.execucao.domain.repository.PagamentoRepository;
import br.contabil.execucao.domain.repository.SaldosExecucaoPort;
import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class RegistrarPagamentoTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private LiquidacaoRepository liquidacaoRepositorio;

    @Mock
    private SaldosExecucaoPort saldos;

    @Mock
    private ExecucaoContabilPort escrituracao;

    @Mock
    private PagamentoRepository repositorio;

    @Mock
    private IdempotenciaPagamentoRepository idempotencia;

    @Mock
    private PublicacaoTransparenciaExecucaoPort publicacaoTransparencia;

    @Mock
    private AuditoriaEscrita auditoria;

    private RegistrarPagamento useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final LiquidacaoId liquidacaoId = LiquidacaoId.novo();
    private final LocalDate dataCompetencia = LocalDate.of(2026, 7, 20);
    private final Beneficiario fornecedor = new Beneficiario("Fornecedor A", "12345678000199");
    private static final Recurso RECURSO = new Recurso("execucao:pagamento");

    @BeforeEach
    void setUp() {
        Clock relogioFixo = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC);
        useCase = new RegistrarPagamento(
                new ControleAcesso(servicoIdentidade),
                liquidacaoRepositorio,
                saldos,
                escrituracao,
                repositorio,
                idempotencia,
                publicacaoTransparencia,
                auditoria,
                relogioFixo);
    }

    private Liquidacao liquidacaoComStatus(StatusAprovacao status) {
        Liquidacao registrada = Liquidacao.registrar(
                liquidacaoId,
                enteId,
                EmpenhoId.novo(),
                dataCompetencia,
                Dinheiro.de("1000.00"),
                List.of(DocumentoSuporte.de("NF", "123", dataCompetencia)),
                "liquidação NF 123",
                UUID.randomUUID(),
                new Cpf("11111111111"));
        return switch (status) {
            case PENDENTE -> registrada;
            case APROVADA -> registrada.aprovar(new Cpf("22222222222"), new Cpf("33333333333"));
            case DEVOLVIDA -> registrada.devolver(new Cpf("22222222222"), new Cpf("33333333333"), "motivo");
        };
    }

    @Test
    @DisplayName("consulta saldo, escritura fato contábil e persiste pagamento")
    void registraComSucesso() {
        Sessao sessao = sessao();
        ReferenciaFatoContabil fatoContabilId = new ReferenciaFatoContabil(UUID.randomUUID());
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(liquidacaoRepositorio.buscarPorId(enteId, liquidacaoId))
                .thenReturn(Optional.of(liquidacaoComStatus(StatusAprovacao.APROVADA)));
        when(saldos.saldoLiquidacao(enteId, liquidacaoId))
                .thenReturn(new SaldoLiquidacao(liquidacaoId, Dinheiro.de("1000.00"), Dinheiro.de("200.00")));
        when(escrituracao.registrarPagamento(any(SolicitacaoEscrituracaoPagamento.class)))
                .thenReturn(fatoContabilId);

        Pagamento pagamento = useCase.executar(
                sessao,
                enteId,
                liquidacaoId,
                dataCompetencia,
                Dinheiro.de("300.00"),
                NaturezaPagamento.ORCAMENTARIO,
                Optional.of(fornecedor),
                Optional.of("OB-10"),
                "pagamento NF 123",
                Optional.empty());

        assertThat(pagamento.liquidacaoId()).isEqualTo(liquidacaoId);
        assertThat(pagamento.fatoContabilId()).isEqualTo(fatoContabilId.valor());
        verify(repositorio).inserir(pagamento);
        verify(publicacaoTransparencia).publicar(pagamento, sessao);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("execucao_pagamento_registrado");
        assertThat(evento.getValue().detalhes())
                .containsEntry("liquidacaoId", liquidacaoId.valor().toString())
                .containsEntry("fatoContabilId", fatoContabilId.valor().toString())
                .containsEntry("natureza", "ORCAMENTARIO")
                .containsEntry("valor", "300.00")
                .doesNotContainValue(fornecedor.cpfCnpj());
    }

    @Test
    @DisplayName("beneficiário ausente falha antes de consultar saldos ou escriturar")
    void rejeitaBeneficiarioAusenteAntesDeIo() {
        Sessao sessao = sessao();
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(
                        sessao,
                        enteId,
                        liquidacaoId,
                        dataCompetencia,
                        Dinheiro.de("300.00"),
                        NaturezaPagamento.ORCAMENTARIO,
                        Optional.empty(),
                        Optional.empty(),
                        "pagamento sem beneficiário",
                        Optional.empty()))
                .isInstanceOf(ExecucaoInvalidaException.class);

        verifyNoInteractions(liquidacaoRepositorio, saldos, escrituracao, repositorio, publicacaoTransparencia, auditoria);
    }

    @Test
    @DisplayName("saldo insuficiente falha antes de escriturar e persistir")
    void rejeitaSemSaldoAntesDeEscriturar() {
        Sessao sessao = sessao();
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(liquidacaoRepositorio.buscarPorId(enteId, liquidacaoId))
                .thenReturn(Optional.of(liquidacaoComStatus(StatusAprovacao.APROVADA)));
        when(saldos.saldoLiquidacao(enteId, liquidacaoId))
                .thenReturn(new SaldoLiquidacao(liquidacaoId, Dinheiro.de("1000.00"), Dinheiro.de("800.00")));

        assertThatThrownBy(() -> useCase.executar(
                        sessao,
                        enteId,
                        liquidacaoId,
                        dataCompetencia,
                        Dinheiro.de("300.00"),
                        NaturezaPagamento.ORCAMENTARIO,
                        Optional.of(fornecedor),
                        Optional.empty(),
                        "pagamento acima do saldo",
                        Optional.empty()))
                .isInstanceOf(SaldoInsuficienteException.class);

        verify(escrituracao, never()).registrarPagamento(any());
        verify(repositorio, never()).inserir(any());
        verify(publicacaoTransparencia, never()).publicar(any(Pagamento.class), any(Sessao.class));
        verify(auditoria, never()).append(any());
    }

    @Test
    @DisplayName("falha da escrituração contábil não persiste pagamento parcial")
    void falhaEscrituracaoNaoPersistePagamento() {
        Sessao sessao = sessao();
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(liquidacaoRepositorio.buscarPorId(enteId, liquidacaoId))
                .thenReturn(Optional.of(liquidacaoComStatus(StatusAprovacao.APROVADA)));
        when(saldos.saldoLiquidacao(enteId, liquidacaoId))
                .thenReturn(new SaldoLiquidacao(liquidacaoId, Dinheiro.de("1000.00"), Dinheiro.de("200.00")));
        when(escrituracao.registrarPagamento(any(SolicitacaoEscrituracaoPagamento.class)))
                .thenThrow(new IllegalStateException("razão indisponível"));

        assertThatThrownBy(() -> useCase.executar(
                        sessao,
                        enteId,
                        liquidacaoId,
                        dataCompetencia,
                        Dinheiro.de("300.00"),
                        NaturezaPagamento.ORCAMENTARIO,
                        Optional.of(fornecedor),
                        Optional.empty(),
                        "pagamento NF 123",
                        Optional.empty()))
                .isInstanceOf(IllegalStateException.class);

        verify(repositorio, never()).inserir(any());
        verify(publicacaoTransparencia, never()).publicar(any(Pagamento.class), any(Sessao.class));
        verify(auditoria, never()).append(any());
    }

    @Test
    @DisplayName("tenant divergente falha antes do RBAC e dos ports")
    void negaTenantDivergente() {
        Sessao sessaoOutroEnte = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                TenantId.de(UUID.randomUUID().toString()),
                Optional.empty(),
                true,
                Instant.parse("2030-01-01T00:00:00Z"));

        assertThatThrownBy(() -> useCase.executar(
                        sessaoOutroEnte,
                        enteId,
                        liquidacaoId,
                        dataCompetencia,
                        Dinheiro.de("300.00"),
                        NaturezaPagamento.ORCAMENTARIO,
                        Optional.of(fornecedor),
                        Optional.empty(),
                        "pagamento NF 123",
                        Optional.empty()))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(liquidacaoRepositorio, saldos, escrituracao, repositorio, publicacaoTransparencia, auditoria);
    }

    @Test
    @DisplayName("ADR-0023: pagar liquidação pendente de aprovação é recusado com pagamento_nao_aprovado")
    void rejeitaPagamentoDeLiquidacaoNaoAprovada() {
        Sessao sessao = sessao();
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(liquidacaoRepositorio.buscarPorId(enteId, liquidacaoId))
                .thenReturn(Optional.of(liquidacaoComStatus(StatusAprovacao.PENDENTE)));

        assertThatThrownBy(() -> useCase.executar(
                        sessao,
                        enteId,
                        liquidacaoId,
                        dataCompetencia,
                        Dinheiro.de("300.00"),
                        NaturezaPagamento.ORCAMENTARIO,
                        Optional.of(fornecedor),
                        Optional.empty(),
                        "pagamento de liquidação não aprovada",
                        Optional.empty()))
                .isInstanceOf(PagamentoNaoAprovadoException.class)
                .satisfies(erro -> assertThat(((PagamentoNaoAprovadoException) erro).codigo())
                        .isEqualTo("pagamento_nao_aprovado"));

        verifyNoInteractions(saldos, escrituracao, repositorio, publicacaoTransparencia, auditoria);
    }

    @Test
    @DisplayName("ADR-0023: pagar liquidação devolvida também é recusado (só 'aprovada' libera o pagamento)")
    void rejeitaPagamentoDeLiquidacaoDevolvida() {
        Sessao sessao = sessao();
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(liquidacaoRepositorio.buscarPorId(enteId, liquidacaoId))
                .thenReturn(Optional.of(liquidacaoComStatus(StatusAprovacao.DEVOLVIDA)));

        assertThatThrownBy(() -> useCase.executar(
                        sessao,
                        enteId,
                        liquidacaoId,
                        dataCompetencia,
                        Dinheiro.de("300.00"),
                        NaturezaPagamento.ORCAMENTARIO,
                        Optional.of(fornecedor),
                        Optional.empty(),
                        "pagamento de liquidação devolvida",
                        Optional.empty()))
                .isInstanceOf(PagamentoNaoAprovadoException.class);

        verifyNoInteractions(saldos, escrituracao, repositorio, publicacaoTransparencia, auditoria);
    }

    @Test
    @DisplayName("RAZ-134: chave nova reserva antes de escriturar e segue o fluxo normal")
    void chaveNovaReservaAntesDeEscriturarESegueFluxoNormal() {
        Sessao sessao = sessao();
        ReferenciaFatoContabil fatoContabilId = new ReferenciaFatoContabil(UUID.randomUUID());
        ChaveIdempotencia chave = ChaveIdempotencia.de("chave-nova-1");
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(liquidacaoRepositorio.buscarPorId(enteId, liquidacaoId))
                .thenReturn(Optional.of(liquidacaoComStatus(StatusAprovacao.APROVADA)));
        when(saldos.saldoLiquidacao(enteId, liquidacaoId))
                .thenReturn(new SaldoLiquidacao(liquidacaoId, Dinheiro.de("1000.00"), Dinheiro.de("200.00")));
        when(escrituracao.registrarPagamento(any(SolicitacaoEscrituracaoPagamento.class)))
                .thenReturn(fatoContabilId);
        when(idempotencia.reservar(eq(enteId), eq(chave), any(PagamentoId.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(2));

        Pagamento pagamento = useCase.executar(
                sessao,
                enteId,
                liquidacaoId,
                dataCompetencia,
                Dinheiro.de("300.00"),
                NaturezaPagamento.ORCAMENTARIO,
                Optional.of(fornecedor),
                Optional.of("OB-10"),
                "pagamento NF 123",
                Optional.of(chave));

        verify(idempotencia).reservar(eq(enteId), eq(chave), any(PagamentoId.class));
        verify(repositorio).inserir(pagamento);
        verify(publicacaoTransparencia).publicar(pagamento, sessao);
    }

    @Test
    @DisplayName("RAZ-134: reenvio com a mesma chave devolve o pagamento original sem repetir o efeito")
    void reenvioComMesmaChaveDevolveOPagamentoOriginalSemRepetirOEfeito() {
        Sessao sessao = sessao();
        ChaveIdempotencia chave = ChaveIdempotencia.de("chave-reenvio-1");
        Pagamento pagamentoOriginal = Pagamento.registrar(
                PagamentoId.novo(),
                enteId,
                liquidacaoId,
                dataCompetencia,
                Dinheiro.de("300.00"),
                NaturezaPagamento.ORCAMENTARIO,
                Optional.of(fornecedor),
                Optional.of("OB-10"),
                "pagamento NF 123",
                UUID.randomUUID());
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(idempotencia.reservar(eq(enteId), eq(chave), any(PagamentoId.class)))
                .thenReturn(pagamentoOriginal.id());
        when(repositorio.buscarPorId(enteId, pagamentoOriginal.id())).thenReturn(Optional.of(pagamentoOriginal));

        Pagamento pagamento = useCase.executar(
                sessao,
                enteId,
                liquidacaoId,
                dataCompetencia,
                Dinheiro.de("300.00"),
                NaturezaPagamento.ORCAMENTARIO,
                Optional.of(fornecedor),
                Optional.of("OB-10"),
                "pagamento NF 123",
                Optional.of(chave));

        assertThat(pagamento).isEqualTo(pagamentoOriginal);
        // Reenvio (mesma chave já usada): nem consulta liquidação/saldo nem repete a
        // escrituração/persistência/publicação — mesmo que o saldo já esteja consumido.
        verifyNoInteractions(liquidacaoRepositorio, saldos, escrituracao, publicacaoTransparencia, auditoria);
        verify(repositorio, never()).inserir(any());
    }

    private Sessao sessao() {
        return new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                enteId,
                Optional.empty(),
                true,
                Instant.parse("2030-01-01T00:00:00Z"));
    }
}
