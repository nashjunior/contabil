package br.contabil.razao.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.EncerramentoConflitanteException;
import br.contabil.razao.domain.EncerramentoExercicioPendenteRestosAPagarException;
import br.contabil.razao.domain.PeriodoContabil;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.PeriodoContabilNaoEncontradoException;
import br.contabil.razao.domain.StatusPeriodo;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.PeriodoContabilRepository;

@ExtendWith(MockitoExtension.class)
class EncerrarExercicioTest {

    private static final ServicoIdentidade.Recurso RECURSO_PERIODO =
            new ServicoIdentidade.Recurso("razao:periodo_contabil");

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private PeriodoContabilRepository periodoRepositorio;

    private EncerrarExercicio useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());

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
        useCase = new EncerrarExercicio(new ControleAcesso(servicoIdentidade), periodoRepositorio);
    }

    @Test
    @DisplayName("mês 13 aberto fica bloqueado por Restos a Pagar antes de qualquer escrita")
    void bloqueiaAteContratoRestosAPagar() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        PeriodoContabil periodoExercicio = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(), enteId, 2026, 13, StatusPeriodo.ABERTO, Optional.empty());
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 13)).thenReturn(Optional.of(periodoExercicio));

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026))
                .isInstanceOf(EncerramentoExercicioPendenteRestosAPagarException.class)
                .satisfies(erro -> assertThat(((EncerramentoExercicioPendenteRestosAPagarException) erro).codigo())
                        .isEqualTo("encerramento_exercicio_pendente_restos_a_pagar"));

        verify(periodoRepositorio).buscarPorCompetencia(enteId, 2026, 13);
        verify(periodoRepositorio, never()).encerrar(any());
    }

    @Test
    @DisplayName("mês 13 inexistente retorna o contrato de período não encontrado")
    void rejeitaPeriodoExercicioInexistente() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 13)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026))
                .isInstanceOf(PeriodoContabilNaoEncontradoException.class);

        verify(periodoRepositorio, never()).encerrar(any());
    }

    @Test
    @DisplayName("mês 13 já encerrado mantém o erro de conflito sem gerar novos fatos")
    void rejeitaExercicioJaEncerrado() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        PeriodoContabil periodoExercicio = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(),
                enteId,
                2026,
                13,
                StatusPeriodo.ENCERRADO,
                Optional.of(Instant.parse("2026-12-31T23:00:00Z")));
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 13)).thenReturn(Optional.of(periodoExercicio));

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026))
                .isInstanceOf(EncerramentoConflitanteException.class);

        verify(periodoRepositorio, never()).encerrar(any());
    }

    @Test
    @DisplayName("RAZ-33 deny: RBAC nega o encerramento de exercício sem tocar repositórios")
    void negaSemAutorizacaoDoRbac() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026)).isInstanceOf(SemPermissaoException.class);

        verifyNoInteractions(periodoRepositorio);
    }

    @Test
    @DisplayName("RAZ-33: ENCERRAR exige MFA antes de consultar o período")
    void negaSemMfa() {
        Sessao sessao = sessaoSemMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026)).isInstanceOf(MfaRequeridoException.class);

        verifyNoInteractions(periodoRepositorio);
    }

    @Test
    @DisplayName("RAZ-33: tenant divergente nunca consulta RBAC nem repositórios")
    void negaTenantDivergente() {
        Sessao sessaoDeOutroEnte = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                TenantId.de(UUID.randomUUID().toString()),
                Optional.empty(),
                true,
                Instant.parse("2030-01-01T00:00:00Z"));

        assertThatThrownBy(() -> useCase.executar(sessaoDeOutroEnte, enteId, 2026))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(periodoRepositorio);
    }

    @Test
    @DisplayName("ADR-0045: tipo de evento dedicado para fatos append-only de encerramento")
    void tipoEventoEncerramentoTemCodigoEstavel() {
        assertThat(TipoEvento.ENCERRAMENTO.codigo()).isEqualTo("encerramento");
        assertThat(TipoEvento.deCodigo("encerramento")).isEqualTo(TipoEvento.ENCERRAMENTO);
    }
}
