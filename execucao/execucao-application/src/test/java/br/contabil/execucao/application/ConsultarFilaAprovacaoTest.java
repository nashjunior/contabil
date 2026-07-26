package br.contabil.execucao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.execucao.domain.FiltroFilaAprovacao;
import br.contabil.execucao.domain.PaginaFilaAprovacao;
import br.contabil.execucao.domain.StatusAprovacao;
import br.contabil.execucao.domain.repository.FilaAprovacaoQuery;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class ConsultarFilaAprovacaoTest {

    private static final TenantId ENTE = new TenantId(UUID.randomUUID());
    private static final Cpf SOLICITANTE = new Cpf("55566677788");

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private FilaAprovacaoQuery filaAprovacao;

    private ConsultarFilaAprovacao consultarFilaAprovacao() {
        return new ConsultarFilaAprovacao(new ControleAcesso(servicoIdentidade), filaAprovacao);
    }

    private Sessao sessao() {
        return new Sessao(UUID.randomUUID(), SOLICITANTE, ENTE, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void exigeLerSobreLiquidacaoEDelegaComOSolicitanteDaSessao() {
        when(servicoIdentidade.autorizar(any(), eq(new Recurso("execucao:liquidacao")), eq(Acao.LER)))
                .thenReturn(true);
        PaginaFilaAprovacao esperado = new PaginaFilaAprovacao(List.of(), Optional.empty());
        when(filaAprovacao.consultar(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(esperado);

        FiltroFilaAprovacao filtro = FiltroFilaAprovacao.porStatus(StatusAprovacao.PENDENTE);
        PaginaFilaAprovacao pagina = consultarFilaAprovacao()
                .executar(sessao(), ENTE, filtro, Optional.empty(), Optional.empty());

        assertThat(pagina).isSameAs(esperado);
        ArgumentCaptor<Cpf> solicitante = ArgumentCaptor.forClass(Cpf.class);
        verify(filaAprovacao).consultar(eq(ENTE), solicitante.capture(), eq(filtro), org.mockito.ArgumentMatchers.anyInt(), eq(Optional.empty()));
        assertThat(solicitante.getValue()).isEqualTo(SOLICITANTE);
    }

    @Test
    void clampaOLimiteEntre1E100ComDefault20() {
        when(servicoIdentidade.autorizar(any(), any(), any())).thenReturn(true);
        when(filaAprovacao.consultar(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(new PaginaFilaAprovacao(List.of(), Optional.empty()));
        FiltroFilaAprovacao filtro = FiltroFilaAprovacao.porStatus(StatusAprovacao.PENDENTE);

        consultarFilaAprovacao().executar(sessao(), ENTE, filtro, Optional.empty(), Optional.empty());
        consultarFilaAprovacao().executar(sessao(), ENTE, filtro, Optional.of(500), Optional.empty());
        consultarFilaAprovacao().executar(sessao(), ENTE, filtro, Optional.of(0), Optional.empty());
        consultarFilaAprovacao().executar(sessao(), ENTE, filtro, Optional.of(37), Optional.empty());

        ArgumentCaptor<Integer> limite = ArgumentCaptor.forClass(Integer.class);
        verify(filaAprovacao, org.mockito.Mockito.times(4))
                .consultar(any(), any(), any(), limite.capture(), any());
        assertThat(limite.getAllValues()).containsExactly(20, 100, 20, 37);
    }

    @Test
    void rbacNegadoNaoConsultaAFila() {
        when(servicoIdentidade.autorizar(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> consultarFilaAprovacao()
                        .executar(sessao(), ENTE, FiltroFilaAprovacao.porStatus(StatusAprovacao.PENDENTE), Optional.empty(), Optional.empty()))
                .isInstanceOf(ServicoIdentidade.SemPermissaoException.class);

        verify(filaAprovacao, never()).consultar(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void tenantDivergenteDaSessaoNegaAntesDeConsultar() {
        assertThatThrownBy(() -> consultarFilaAprovacao().executar(
                        sessao(),
                        new TenantId(UUID.randomUUID()),
                        FiltroFilaAprovacao.porStatus(StatusAprovacao.PENDENTE),
                        Optional.empty(),
                        Optional.empty()))
                .isInstanceOf(ServicoIdentidade.SemPermissaoException.class);

        verifyNoInteractions(filaAprovacao);
    }
}
