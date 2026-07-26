package br.contabil.execucao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.TrilhaLiquidacao;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaLeitura;
import br.contabil.plataforma.domain.auditoria.AuditoriaLeitura.FiltroAuditoria;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.consulta.ConsultaPaginada;
import br.contabil.plataforma.domain.consulta.Paginacao;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class ConsultarTrilhaLiquidacaoTest {

    private static final TenantId ENTE = new TenantId(UUID.randomUUID());
    private static final LiquidacaoId LIQUIDACAO = LiquidacaoId.novo();
    private static final UUID EMPENHO = UUID.randomUUID();
    private static final String RECURSO_LIQUIDACAO = "execucao:liquidacao:" + LIQUIDACAO.valor();
    private static final String RECURSO_EMPENHO = "execucao:empenho:" + EMPENHO;
    private static final String ATOR_MASCARADO = "***.456.***-**";

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private AuditoriaLeitura auditoriaLeitura;

    private ConsultarTrilhaLiquidacao consultarTrilhaLiquidacao() {
        return new ConsultarTrilhaLiquidacao(new ControleAcesso(servicoIdentidade), auditoriaLeitura);
    }

    private Sessao sessao() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("99988877766"), ENTE, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    private EventoAuditoria evento(String tipo, String recurso, Instant momento, Map<String, String> detalhes) {
        return new EventoAuditoria(ENTE, tipo, ATOR_MASCARADO, recurso, momento, detalhes);
    }

    @Test
    void montaCadeiaEmpenhoLiquidacaoDecisaoOrdenadaPorMomentoDescartandoRuido() {
        when(servicoIdentidade.autorizar(any(), eq(new Recurso("execucao:liquidacao")), eq(Acao.LER)))
                .thenReturn(true);

        EventoAuditoria empenhoRegistrado = evento(
                "execucao_empenho_registrado", RECURSO_EMPENHO, Instant.parse("2026-07-10T00:00:00Z"), Map.of());
        EventoAuditoria liquidacaoRegistrada = evento(
                "execucao_liquidacao_registrada",
                RECURSO_LIQUIDACAO,
                Instant.parse("2026-07-11T00:00:00Z"),
                Map.of("empenhoId", EMPENHO.toString()));
        EventoAuditoria ruido = evento(
                "execucao_liquidacao_transparencia_sinalizada",
                RECURSO_LIQUIDACAO,
                Instant.parse("2026-07-12T00:00:00Z"),
                Map.of());
        EventoAuditoria decidida = evento(
                "execucao_pagamento_aprovacao_decidida",
                RECURSO_LIQUIDACAO,
                Instant.parse("2026-07-13T00:00:00Z"),
                Map.of("decisao", "APROVADA"));

        when(auditoriaLeitura.consultar(eq(ENTE), any())).thenAnswer(invocacao -> {
            ConsultaPaginada<FiltroAuditoria> consulta = invocacao.getArgument(1);
            String recurso = consulta.filtro().recurso().orElseThrow();
            if (recurso.equals(RECURSO_LIQUIDACAO)) {
                return new Paginacao<>(1, 100, 3, List.of(liquidacaoRegistrada, ruido, decidida));
            }
            if (recurso.equals(RECURSO_EMPENHO)) {
                return new Paginacao<>(1, 100, 1, List.of(empenhoRegistrado));
            }
            return new Paginacao<>(1, 100, 0, List.of());
        });

        TrilhaLiquidacao trilha = consultarTrilhaLiquidacao().executar(sessao(), ENTE, LIQUIDACAO);

        assertThat(trilha.liquidacaoId()).isEqualTo(LIQUIDACAO);
        assertThat(trilha.eventos())
                .extracting(EventoAuditoria::tipo)
                .containsExactly(
                        "execucao_empenho_registrado",
                        "execucao_liquidacao_registrada",
                        "execucao_pagamento_aprovacao_decidida");
        assertThat(trilha.eventos()).allSatisfy(e -> assertThat(e.ator()).isEqualTo(ATOR_MASCARADO));
    }

    @Test
    void consultaOEmpenhoDaCadeiaFiltrandoPorRecursoETipoDeRegistro() {
        when(servicoIdentidade.autorizar(any(), any(), any())).thenReturn(true);
        EventoAuditoria liquidacaoRegistrada = evento(
                "execucao_liquidacao_registrada",
                RECURSO_LIQUIDACAO,
                Instant.parse("2026-07-11T00:00:00Z"),
                Map.of("empenhoId", EMPENHO.toString()));
        when(auditoriaLeitura.consultar(eq(ENTE), any())).thenAnswer(invocacao -> {
            ConsultaPaginada<FiltroAuditoria> consulta = invocacao.getArgument(1);
            String recurso = consulta.filtro().recurso().orElseThrow();
            return recurso.equals(RECURSO_LIQUIDACAO)
                    ? new Paginacao<>(1, 100, 1, List.of(liquidacaoRegistrada))
                    : new Paginacao<>(1, 100, 0, List.of());
        });

        consultarTrilhaLiquidacao().executar(sessao(), ENTE, LIQUIDACAO);

        ArgumentCaptor<ConsultaPaginada<FiltroAuditoria>> consulta =
                ArgumentCaptor.forClass(ConsultaPaginada.class);
        verify(auditoriaLeitura, org.mockito.Mockito.times(2)).consultar(eq(ENTE), consulta.capture());
        FiltroAuditoria filtroEmpenho = consulta.getAllValues().stream()
                .map(ConsultaPaginada::filtro)
                .filter(f -> f.recurso().map(RECURSO_EMPENHO::equals).orElse(false))
                .findFirst()
                .orElseThrow();
        assertThat(filtroEmpenho.tipo()).contains("execucao_empenho_registrado");
    }

    @Test
    void semEventoDeRegistroDaLiquidacaoNaoConsultaOEmpenho() {
        when(servicoIdentidade.autorizar(any(), any(), any())).thenReturn(true);
        when(auditoriaLeitura.consultar(eq(ENTE), any())).thenReturn(new Paginacao<>(1, 100, 0, List.of()));

        TrilhaLiquidacao trilha = consultarTrilhaLiquidacao().executar(sessao(), ENTE, LIQUIDACAO);

        assertThat(trilha.eventos()).isEmpty();
        verify(auditoriaLeitura, org.mockito.Mockito.times(1)).consultar(eq(ENTE), any());
    }

    @Test
    void rbacNegadoNaoLeAAuditoria() {
        when(servicoIdentidade.autorizar(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> consultarTrilhaLiquidacao().executar(sessao(), ENTE, LIQUIDACAO))
                .isInstanceOf(ServicoIdentidade.SemPermissaoException.class);

        verify(auditoriaLeitura, never()).consultar(any(), any());
    }
}
