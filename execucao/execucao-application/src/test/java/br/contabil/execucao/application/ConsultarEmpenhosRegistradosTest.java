package br.contabil.execucao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.execucao.domain.PaginaEmpenhosRegistrados;
import br.contabil.execucao.domain.repository.EmpenhosRegistradosQuery;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class ConsultarEmpenhosRegistradosTest {

    private static final TenantId ENTE = new TenantId(UUID.randomUUID());
    private static final Cpf SOLICITANTE = new Cpf("55566677788");

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private EmpenhosRegistradosQuery empenhosRegistrados;

    private ConsultarEmpenhosRegistrados consultar() {
        return new ConsultarEmpenhosRegistrados(new ControleAcesso(servicoIdentidade), empenhosRegistrados);
    }

    private Sessao sessao() {
        return new Sessao(UUID.randomUUID(), SOLICITANTE, ENTE, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void exigeLerSobreEmpenhoEDelegaOsFiltrosAoPort() {
        when(servicoIdentidade.autorizar(any(), eq(new Recurso("execucao:empenho")), eq(Acao.LER))).thenReturn(true);
        PaginaEmpenhosRegistrados esperado = new PaginaEmpenhosRegistrados(List.of(), Optional.empty());
        when(empenhosRegistrados.consultar(any(), any(), any(), any(), ArgumentMatchers.anyInt(), any()))
                .thenReturn(esperado);

        PaginaEmpenhosRegistrados pagina = consultar().executar(
                sessao(), ENTE, Optional.of(2026), Optional.of(LocalDate.of(2026, 1, 1)),
                Optional.of(LocalDate.of(2026, 12, 31)), Optional.empty(), Optional.empty());

        assertThat(pagina).isSameAs(esperado);
        verify(empenhosRegistrados).consultar(
                eq(ENTE), eq(Optional.of(2026)), eq(Optional.of(LocalDate.of(2026, 1, 1))),
                eq(Optional.of(LocalDate.of(2026, 12, 31))), ArgumentMatchers.anyInt(), eq(Optional.empty()));
    }

    @Test
    void clampaOLimiteEntre1E100ComDefault20() {
        when(servicoIdentidade.autorizar(any(), any(), any())).thenReturn(true);
        when(empenhosRegistrados.consultar(any(), any(), any(), any(), ArgumentMatchers.anyInt(), any()))
                .thenReturn(new PaginaEmpenhosRegistrados(List.of(), Optional.empty()));

        consultar().executar(sessao(), ENTE, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        consultar().executar(sessao(), ENTE, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(500), Optional.empty());
        consultar().executar(sessao(), ENTE, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(0), Optional.empty());
        consultar().executar(sessao(), ENTE, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(37), Optional.empty());

        ArgumentCaptor<Integer> limite = ArgumentCaptor.forClass(Integer.class);
        verify(empenhosRegistrados, times(4)).consultar(any(), any(), any(), any(), limite.capture(), any());
        assertThat(limite.getAllValues()).containsExactly(20, 100, 20, 37);
    }

    @Test
    void rbacNegadoNaoConsulta() {
        when(servicoIdentidade.autorizar(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> consultar().executar(
                        sessao(), ENTE, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(ServicoIdentidade.SemPermissaoException.class);

        verify(empenhosRegistrados, never()).consultar(any(), any(), any(), any(), ArgumentMatchers.anyInt(), any());
    }

    @Test
    void tenantDivergenteDaSessaoNegaAntesDeConsultar() {
        assertThatThrownBy(() -> consultar().executar(
                        sessao(), new TenantId(UUID.randomUUID()), Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()))
                .isInstanceOf(ServicoIdentidade.SemPermissaoException.class);

        verifyNoInteractions(empenhosRegistrados);
        Mockito.verifyNoInteractions(servicoIdentidade);
    }
}
