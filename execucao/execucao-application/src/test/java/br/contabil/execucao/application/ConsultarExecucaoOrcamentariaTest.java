package br.contabil.execucao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.execucao.domain.ExecucaoOrcamentariaPeriodo;
import br.contabil.execucao.domain.repository.ExecucaoOrcamentariaPeriodoPort;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class ConsultarExecucaoOrcamentariaTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private ExecucaoOrcamentariaPeriodoPort execucaoOrcamentariaPort;

    private ConsultarExecucaoOrcamentaria useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private static final Recurso RECURSO = new Recurso("execucao:orcamento");

    private Sessao sessaoSemMfa() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        useCase = new ConsultarExecucaoOrcamentaria(new ControleAcesso(servicoIdentidade), execucaoOrcamentariaPort);
    }

    @Test
    @DisplayName("devolve a execução do período do port quando o RBAC autoriza — LER nunca exige MFA")
    void devolveExecucaoAutorizada() {
        Sessao sessao = sessaoSemMfa();
        ExecucaoOrcamentariaPeriodo esperado = new ExecucaoOrcamentariaPeriodo(
                enteId, 2026, 6, Dinheiro.de("1000.00"), Dinheiro.de("700.00"), Dinheiro.de("300.00"));
        when(servicoIdentidade.autorizar(sessao, RECURSO, ServicoIdentidade.Acao.LER)).thenReturn(true);
        when(execucaoOrcamentariaPort.execucaoDoPeriodo(enteId, 2026, 6)).thenReturn(esperado);

        ExecucaoOrcamentariaPeriodo execucao = useCase.executar(sessao, enteId, 2026, 6);

        assertThat(execucao).isSameAs(esperado);
    }

    @Test
    @DisplayName("RBAC nega a consulta — SemPermissaoException sem tocar no port")
    void negaSemAutorizacaoDoRbac() {
        Sessao sessao = sessaoSemMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO, ServicoIdentidade.Acao.LER)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026, 6))
                .isInstanceOf(SemPermissaoException.class);

        verifyNoInteractions(execucaoOrcamentariaPort);
    }

    @Test
    @DisplayName("tenant da requisição divergente da sessão nunca consulta o RBAC nem o port")
    void negaTenantDivergente() {
        Sessao sessaoDeOutroEnte = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                TenantId.de(UUID.randomUUID().toString()),
                Optional.empty(),
                false,
                Instant.parse("2030-01-01T00:00:00Z"));

        assertThatThrownBy(() -> useCase.executar(sessaoDeOutroEnte, enteId, 2026, 6))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(execucaoOrcamentariaPort);
    }
}
