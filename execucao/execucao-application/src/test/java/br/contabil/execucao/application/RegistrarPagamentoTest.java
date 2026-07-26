package br.contabil.execucao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.contabil.execucao.domain.Beneficiario;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.NaturezaPagamento;
import br.contabil.execucao.domain.Pagamento;
import br.contabil.execucao.domain.SaldoInsuficienteException;
import br.contabil.execucao.domain.SaldoLiquidacao;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort.SolicitacaoEscrituracaoPagamento;
import br.contabil.execucao.domain.repository.PagamentoRepository;
import br.contabil.execucao.domain.repository.SaldosExecucaoPort;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
class RegistrarPagamentoTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private SaldosExecucaoPort saldos;

    @Mock
    private ExecucaoContabilPort escrituracao;

    @Mock
    private PagamentoRepository repositorio;

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
                saldos,
                escrituracao,
                repositorio,
                publicacaoTransparencia,
                auditoria,
                relogioFixo);
    }

    @Test
    @DisplayName("consulta saldo, escritura fato contábil e persiste pagamento")
    void registraComSucesso() {
        Sessao sessao = sessao();
        UUID fatoContabilId = UUID.randomUUID();
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
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
                "pagamento NF 123");

        assertThat(pagamento.liquidacaoId()).isEqualTo(liquidacaoId);
        assertThat(pagamento.fatoContabilId()).isEqualTo(fatoContabilId);
        verify(repositorio).inserir(pagamento);
        verify(publicacaoTransparencia).publicar(pagamento, sessao);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("execucao_pagamento_registrado");
        assertThat(evento.getValue().detalhes())
                .containsEntry("liquidacaoId", liquidacaoId.valor().toString())
                .containsEntry("fatoContabilId", fatoContabilId.toString())
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
                        "pagamento sem beneficiário"))
                .isInstanceOf(ExecucaoInvalidaException.class);

        verifyNoInteractions(saldos, escrituracao, repositorio, publicacaoTransparencia, auditoria);
    }

    @Test
    @DisplayName("saldo insuficiente falha antes de escriturar e persistir")
    void rejeitaSemSaldoAntesDeEscriturar() {
        Sessao sessao = sessao();
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
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
                        "pagamento acima do saldo"))
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
                        "pagamento NF 123"))
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
                        "pagamento NF 123"))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(saldos, escrituracao, repositorio, publicacaoTransparencia, auditoria);
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
