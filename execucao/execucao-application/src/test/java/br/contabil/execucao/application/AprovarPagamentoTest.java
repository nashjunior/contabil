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
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.execucao.domain.AutoAprovacaoNaoPermitidaException;
import br.contabil.execucao.domain.CredorId;
import br.contabil.execucao.domain.DecisaoAprovacao;
import br.contabil.execucao.domain.DocumentoSuporte;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.StatusAprovacao;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.execucao.domain.repository.LiquidacaoRepository;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class AprovarPagamentoTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private LiquidacaoRepository liquidacaoRepositorio;

    @Mock
    private EmpenhoRepository empenhoRepositorio;

    @Mock
    private AuditoriaEscrita auditoria;

    private AprovarPagamento useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final LiquidacaoId liquidacaoId = LiquidacaoId.novo();
    private final EmpenhoId empenhoId = EmpenhoId.novo();
    private final Cpf autorLiquidacao = new Cpf("11111111111");
    private final Cpf autorEmpenho = new Cpf("22222222222");
    private final Cpf aprovador = new Cpf("33333333333");
    private static final Recurso RECURSO = new Recurso("execucao:pagamento");

    @BeforeEach
    void setUp() {
        Clock relogioFixo = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC);
        useCase = new AprovarPagamento(
                new ControleAcesso(servicoIdentidade), liquidacaoRepositorio, empenhoRepositorio, auditoria, relogioFixo);
    }

    private Liquidacao liquidacaoPendente() {
        return Liquidacao.registrar(
                liquidacaoId,
                enteId,
                empenhoId,
                LocalDate.of(2026, 7, 15),
                Dinheiro.de("1000.00"),
                List.of(DocumentoSuporte.de("NF", "123", LocalDate.of(2026, 7, 10))),
                "liquidação NF 123",
                UUID.randomUUID(),
                autorLiquidacao);
    }

    private Empenho empenho() {
        return Empenho.registrar(
                empenhoId,
                enteId,
                1L,
                2026,
                TipoEmpenho.ORDINARIO,
                DotacaoId.novo(),
                CredorId.novo(),
                UnidadeGestoraId.novo(),
                null,
                Dinheiro.de("1000.00"),
                LocalDate.of(2026, 7, 1),
                "04.122.0001.2001",
                "0100000000",
                "empenho da NF 123",
                new ReferenciaFatoContabil(UUID.randomUUID()),
                autorEmpenho);
    }

    private Sessao sessao(Cpf titular, boolean mfaConcluido) {
        return new Sessao(UUID.randomUUID(), titular, enteId, Optional.empty(), mfaConcluido, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("ADR-0023: aprova liquidação pendente, transiciona estado e grava auditoria")
    void aprovaComSucesso() {
        Sessao sessao = sessao(aprovador, true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.APROVAR)).thenReturn(true);
        when(liquidacaoRepositorio.buscarPorId(enteId, liquidacaoId)).thenReturn(Optional.of(liquidacaoPendente()));
        when(empenhoRepositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(empenho()));

        Liquidacao decidida = useCase.executar(sessao, enteId, liquidacaoId, DecisaoAprovacao.APROVAR, Optional.empty());

        assertThat(decidida.statusAprovacao()).isEqualTo(StatusAprovacao.APROVADA);
        assertThat(decidida.aprovador()).contains(aprovador);
        verify(liquidacaoRepositorio).atualizarDecisaoAprovacao(decidida);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("execucao_pagamento_aprovacao_decidida");
        assertThat(evento.getValue().detalhes()).containsEntry("decisao", "APROVADA");
    }

    @Test
    @DisplayName("ADR-0023: devolve liquidação pendente com motivo obrigatório")
    void devolveComMotivo() {
        Sessao sessao = sessao(aprovador, true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.APROVAR)).thenReturn(true);
        when(liquidacaoRepositorio.buscarPorId(enteId, liquidacaoId)).thenReturn(Optional.of(liquidacaoPendente()));
        when(empenhoRepositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(empenho()));

        Liquidacao decidida = useCase.executar(
                sessao, enteId, liquidacaoId, DecisaoAprovacao.DEVOLVER, Optional.of("documento fiscal divergente"));

        assertThat(decidida.statusAprovacao()).isEqualTo(StatusAprovacao.DEVOLVIDA);
        assertThat(decidida.motivoDevolucao()).contains("documento fiscal divergente");
        verify(liquidacaoRepositorio).atualizarDecisaoAprovacao(decidida);
    }

    @Test
    @DisplayName("devolver sem motivo é recusado antes de persistir a decisão")
    void devolverSemMotivoFalha() {
        Sessao sessao = sessao(aprovador, true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.APROVAR)).thenReturn(true);
        when(liquidacaoRepositorio.buscarPorId(enteId, liquidacaoId)).thenReturn(Optional.of(liquidacaoPendente()));
        when(empenhoRepositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(empenho()));

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, liquidacaoId, DecisaoAprovacao.DEVOLVER, Optional.empty()))
                .isInstanceOf(ExecucaoInvalidaException.class)
                .hasMessageContaining("motivo");

        verify(liquidacaoRepositorio, never()).atualizarDecisaoAprovacao(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("Regra 9/ADR-0023: recusa auto-aprovação quando o aprovador é o autor da liquidação")
    void recusaAutoAprovacaoContraAutorDaLiquidacao() {
        Sessao sessao = sessao(autorLiquidacao, true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.APROVAR)).thenReturn(true);
        when(liquidacaoRepositorio.buscarPorId(enteId, liquidacaoId)).thenReturn(Optional.of(liquidacaoPendente()));
        when(empenhoRepositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(empenho()));

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, liquidacaoId, DecisaoAprovacao.APROVAR, Optional.empty()))
                .isInstanceOf(AutoAprovacaoNaoPermitidaException.class);

        verify(liquidacaoRepositorio, never()).atualizarDecisaoAprovacao(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("Regra 9/ADR-0023: recusa auto-aprovação quando o aprovador é o autor do empenho da cadeia")
    void recusaAutoAprovacaoContraAutorDoEmpenho() {
        Sessao sessao = sessao(autorEmpenho, true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.APROVAR)).thenReturn(true);
        when(liquidacaoRepositorio.buscarPorId(enteId, liquidacaoId)).thenReturn(Optional.of(liquidacaoPendente()));
        when(empenhoRepositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(empenho()));

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, liquidacaoId, DecisaoAprovacao.APROVAR, Optional.empty()))
                .isInstanceOf(AutoAprovacaoNaoPermitidaException.class);

        verify(liquidacaoRepositorio, never()).atualizarDecisaoAprovacao(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("estado forte: liquidação já decidida não pode ser decidida de novo")
    void rejeitaRedecisaoSobreLiquidacaoJaAprovada() {
        Sessao sessao = sessao(aprovador, true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.APROVAR)).thenReturn(true);
        Liquidacao jaAprovada = liquidacaoPendente().aprovar(aprovador, autorEmpenho);
        when(liquidacaoRepositorio.buscarPorId(enteId, liquidacaoId)).thenReturn(Optional.of(jaAprovada));
        when(empenhoRepositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(empenho()));

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, liquidacaoId, DecisaoAprovacao.DEVOLVER, Optional.of("tarde demais")))
                .isInstanceOf(ExecucaoInvalidaException.class)
                .hasMessageContaining("já foi decidida");

        verify(liquidacaoRepositorio, never()).atualizarDecisaoAprovacao(any());
    }

    @Test
    @DisplayName("liquidação inexistente falha antes de consultar o empenho")
    void rejeitaLiquidacaoInexistente() {
        Sessao sessao = sessao(aprovador, true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.APROVAR)).thenReturn(true);
        when(liquidacaoRepositorio.buscarPorId(enteId, liquidacaoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, liquidacaoId, DecisaoAprovacao.APROVAR, Optional.empty()))
                .isInstanceOf(ExecucaoInvalidaException.class);

        verifyNoInteractions(empenhoRepositorio, auditoria);
    }

    @Test
    @DisplayName("RBAC/MFA bloqueia antes de qualquer consulta de execução")
    void negaSemMfaAntesDeIo() {
        Sessao sessao = sessao(aprovador, false);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.APROVAR)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, liquidacaoId, DecisaoAprovacao.APROVAR, Optional.empty()))
                .isInstanceOf(MfaRequeridoException.class);

        verifyNoInteractions(liquidacaoRepositorio, empenhoRepositorio, auditoria);
    }

    @Test
    @DisplayName("tenant divergente falha antes do RBAC e dos ports")
    void negaTenantDivergente() {
        Sessao sessaoOutroEnte = new Sessao(
                UUID.randomUUID(), aprovador, TenantId.de(UUID.randomUUID().toString()), Optional.empty(), true,
                Instant.parse("2030-01-01T00:00:00Z"));

        assertThatThrownBy(() -> useCase.executar(sessaoOutroEnte, enteId, liquidacaoId, DecisaoAprovacao.APROVAR, Optional.empty()))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(liquidacaoRepositorio, empenhoRepositorio, auditoria);
    }
}
