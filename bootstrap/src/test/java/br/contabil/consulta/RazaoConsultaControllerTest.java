package br.contabil.consulta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.application.ConsultarSaldo;
import br.contabil.razao.application.GerarBalancete;
import br.contabil.razao.domain.Balancete;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.LinhaBalancete;

/**
 * Testa só a adaptação HTTP → use case (RAZ-101): o RBAC/tenant já é coberto
 * por {@code ConsultarSaldoTest}/{@code GerarBalanceteTest} — aqui os use
 * cases são mocks, e o foco é o mapeamento de path/query para os argumentos de
 * {@code executar(..)} e a serialização do resultado no DTO de resposta.
 */
@ExtendWith(MockitoExtension.class)
class RazaoConsultaControllerTest {

    @Mock
    private ConsultarSaldo consultarSaldo;

    @Mock
    private GerarBalancete gerarBalancete;

    /** Mesma serialização da app: {@link DinheiroJacksonModule} emite Dinheiro como string decimal (§6.1). */
    private final ObjectMapper json = new ObjectMapper().registerModule(new DinheiroJacksonModule());

    private RazaoConsultaController controller() {
        return new RazaoConsultaController(consultarSaldo, gerarBalancete);
    }

    private Sessao sessaoDe(TenantId ente) {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), ente, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void saldoAdaptaPathEQueryParaExecutarEDevolveDinheiroComoStringDecimal() throws Exception {
        UUID enteId = UUID.randomUUID();
        UUID contaId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        when(consultarSaldo.executar(sessao, new TenantId(enteId), new ContaContabilId(contaId)))
                .thenReturn(Dinheiro.de("1234.56"));

        SaldoContaResponse resposta = controller().saldo(enteId, contaId, sessao);

        assertThat(resposta.contaId()).isEqualTo(contaId);
        assertThat(resposta.saldo()).isEqualTo(Dinheiro.de("1234.56"));
        // §6.1/ADR-0030 §2: dinheiro serializa como string decimal, nunca número JSON.
        assertThat(json.writeValueAsString(resposta)).contains("\"saldo\":\"1234.56\"");
    }

    @Test
    void saldoPropagaErroDeNegocioSemMapearNoController() {
        UUID enteId = UUID.randomUUID();
        UUID contaId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        when(consultarSaldo.executar(sessao, new TenantId(enteId), new ContaContabilId(contaId)))
                .thenThrow(new SemPermissaoException("sem_permissao"));

        assertThatThrownBy(() -> controller().saldo(enteId, contaId, sessao)).isInstanceOf(SemPermissaoException.class);
    }

    @Test
    void balanceteAdaptaPathEQueryEMapeiaLinhasComNaturezaEDinheiroString() throws Exception {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        UUID contaId = UUID.randomUUID();
        Balancete balancete = new Balancete(
                new TenantId(enteId),
                2026,
                3,
                List.of(new LinhaBalancete(
                        new ContaContabilId(contaId),
                        "1.1.1.01.01",
                        "Caixa",
                        "D",
                        Dinheiro.de("100.00"),
                        Dinheiro.de("50.00"),
                        Dinheiro.de("50.00"),
                        Dinheiro.de("100.00"))));
        when(gerarBalancete.executar(sessao, new TenantId(enteId), 2026, 3)).thenReturn(balancete);

        BalanceteResponse resposta = controller().balancete(enteId, 2026, 3, sessao);

        assertThat(resposta.exercicio()).isEqualTo(2026);
        assertThat(resposta.mes()).isEqualTo(3);
        assertThat(resposta.confere()).isTrue();
        assertThat(resposta.linhas()).hasSize(1);
        BalanceteResponse.Linha linha = resposta.linhas().get(0);
        assertThat(linha.contaId()).isEqualTo(contaId);
        assertThat(linha.codigo()).isEqualTo("1.1.1.01.01");
        assertThat(linha.naturezaSaldo()).isEqualTo("D");
        assertThat(linha.saldoAtual()).isEqualTo(Dinheiro.de("100.00"));
        // §6.1/ADR-0030: dinheiro como string decimal e naturezaSaldo (D/C) no payload.
        String corpo = json.writeValueAsString(resposta);
        assertThat(corpo).contains("\"saldoAtual\":\"100.00\"");
        assertThat(corpo).contains("\"naturezaSaldo\":\"D\"");
    }
}
