package br.contabil.consulta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.contabil.execucao.application.ConsultarExecucaoOrcamentaria;
import br.contabil.execucao.domain.ExecucaoOrcamentariaPeriodo;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class ExecucaoConsultaControllerTest {

    @Mock
    private ConsultarExecucaoOrcamentaria consultarExecucaoOrcamentaria;

    /** Mesma serialização da app: {@link DinheiroJacksonModule} emite Dinheiro como string decimal (§6.1). */
    private final ObjectMapper json = new ObjectMapper().registerModule(new DinheiroJacksonModule());

    private ExecucaoConsultaController controller() {
        return new ExecucaoConsultaController(consultarExecucaoOrcamentaria);
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
}
