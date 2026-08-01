package br.contabil.razao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.PeriodoContabil;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.PeriodoContabilNaoEncontradoException;
import br.contabil.razao.domain.PeriodoExercicioExigeApuracaoException;
import br.contabil.razao.domain.EncerramentoConflitanteException;
import br.contabil.razao.domain.StatusPeriodo;
import br.contabil.razao.domain.repository.PeriodoContabilRepository;

@ExtendWith(MockitoExtension.class)
class EncerrarPeriodoTest {

    private static final ServicoIdentidade.Recurso RECURSO_PERIODO =
            new ServicoIdentidade.Recurso("razao:periodo_contabil");

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private PeriodoContabilRepository periodoRepositorio;

    @Mock
    private AuditoriaEscrita auditoria;

    private EncerrarPeriodo useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final Clock relogioFixo = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);

    private Sessao sessaoComMfa() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), true, Instant.parse("2030-01-01T00:00:00Z"));
    }

    private Sessao sessaoSemMfa() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        useCase = new EncerrarPeriodo(new ControleAcesso(servicoIdentidade), periodoRepositorio, auditoria, relogioFixo);
    }

    @Test
    @DisplayName("encerra um período aberto comum e grava a trilha")
    void encerraPeriodoAberto() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        PeriodoContabil periodo = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(), enteId, 2026, 7, StatusPeriodo.ABERTO, Optional.empty());
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 7)).thenReturn(Optional.of(periodo));
        when(periodoRepositorio.encerrar(any())).thenReturn(true);

        PeriodoContabil encerrado = useCase.executar(sessao, enteId, 2026, 7);

        assertThat(encerrado.status()).isEqualTo(StatusPeriodo.ENCERRADO);
        assertThat(encerrado.encerradoEm()).contains(Instant.parse("2026-08-01T12:00:00Z"));

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("razao_periodo_contabil_encerrado");
        assertThat(evento.getValue().detalhes()).containsEntry("exercicio", "2026").containsEntry("mes", "7");
    }

    @Test
    @DisplayName("rejeita encerrar período inexistente")
    void rejeitaPeriodoInexistente() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026, 7))
                .isInstanceOf(PeriodoContabilNaoEncontradoException.class);

        verify(periodoRepositorio, never()).encerrar(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("corrida: outra chamada já encerrou entre a leitura e a escrita — não sobrescreve, mapeia periodo_ja_encerrado")
    void rejeitaCorridaPerdida() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        PeriodoContabil periodo = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(), enteId, 2026, 7, StatusPeriodo.ABERTO, Optional.empty());
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 7)).thenReturn(Optional.of(periodo));
        when(periodoRepositorio.encerrar(any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026, 7)).isInstanceOf(EncerramentoConflitanteException.class);

        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("mês 13 exige EncerrarExercicio — recusado antes de tocar o repositório de escrita")
    void rejeitaMes13() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        PeriodoContabil periodoExercicio = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(), enteId, 2026, 13, StatusPeriodo.ABERTO, Optional.empty());
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 13)).thenReturn(Optional.of(periodoExercicio));

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026, 13))
                .isInstanceOf(PeriodoExercicioExigeApuracaoException.class);

        verify(periodoRepositorio, never()).encerrar(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RAZ-33 deny: RBAC nega o encerramento — SemPermissaoException sem tocar no repositório")
    void negaSemAutorizacaoDoRbac() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026, 7)).isInstanceOf(SemPermissaoException.class);

        verifyNoInteractions(periodoRepositorio, auditoria);
    }

    @Test
    @DisplayName("RAZ-33: ENCERRAR movimenta recurso — MFA ausente falha antes de buscar o período")
    void negaSemMfa() {
        Sessao sessao = sessaoSemMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026, 7)).isInstanceOf(MfaRequeridoException.class);

        verifyNoInteractions(periodoRepositorio, auditoria);
    }

    @Test
    @DisplayName("RAZ-33: tenant da requisição divergente da sessão nunca consulta o RBAC nem o repositório")
    void negaTenantDivergente() {
        Sessao sessaoDeOutroEnte = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                TenantId.de(UUID.randomUUID().toString()),
                Optional.empty(),
                true,
                Instant.parse("2030-01-01T00:00:00Z"));

        assertThatThrownBy(() -> useCase.executar(sessaoDeOutroEnte, enteId, 2026, 7))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(periodoRepositorio, auditoria);
    }
}
