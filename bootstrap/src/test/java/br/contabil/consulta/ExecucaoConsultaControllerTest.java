package br.contabil.consulta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import br.contabil.execucao.application.ConsultarExecucaoOrcamentaria;
import br.contabil.execucao.application.ConsultarFilaAprovacao;
import br.contabil.execucao.application.ConsultarTrilhaLiquidacao;
import br.contabil.execucao.domain.CredorId;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ExecucaoOrcamentariaPeriodo;
import br.contabil.execucao.domain.FiltroFilaAprovacao;
import br.contabil.execucao.domain.ItemFilaAprovacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.PaginaFilaAprovacao;
import br.contabil.execucao.domain.StatusAprovacao;
import br.contabil.execucao.domain.TrilhaLiquidacao;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
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

    /** Mesma serialização da app: {@link DinheiroJacksonModule} emite Dinheiro como string decimal (§6.1). */
    private final ObjectMapper json = new ObjectMapper().registerModule(new DinheiroJacksonModule());

    private ExecucaoConsultaController controller() {
        return new ExecucaoConsultaController(
                consultarExecucaoOrcamentaria, consultarFilaAprovacao, consultarTrilhaLiquidacao);
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
}
