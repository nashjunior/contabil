package br.contabil.razao.infra;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.application.ParametroEncerramentoOrcamentario;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.Natureza;

@ExtendWith(MockitoExtension.class)
class ParametrosEncerramentoOrcamentarioOficiaisTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());

    @Test
    @DisplayName("RAZ-270: resolve os quatro controles de execução da despesa contra o Crédito Inicial")
    void resolveCodigosPcaspOficiais() {
        UUID creditoDisponivel = UUID.randomUUID();
        UUID empenhadoALiquidar = UUID.randomUUID();
        UUID liquidadoAPagar = UUID.randomUUID();
        UUID empenhadoPago = UUID.randomUUID();
        UUID creditoInicial = UUID.randomUUID();

        mockConta("6.2.2.1.1", creditoDisponivel);
        mockConta("6.2.2.1.3", empenhadoALiquidar);
        mockConta("6.2.2.1.4", liquidadoAPagar);
        mockConta("6.2.2.1.5", empenhadoPago);
        mockConta("5.2.2.1.1.01.00", creditoInicial);

        List<ParametroEncerramentoOrcamentario> parametros =
                new ParametrosEncerramentoOrcamentarioOficiais(jdbcTemplate).para(enteId);

        assertThat(parametros).hasSize(4);
        assertThat(parametros.get(0).contaControle()).isEqualTo(new ContaContabilId(creditoDisponivel));
        assertThat(parametros.get(1).contaControle()).isEqualTo(new ContaContabilId(empenhadoALiquidar));
        assertThat(parametros.get(2).contaControle()).isEqualTo(new ContaContabilId(liquidadoAPagar));
        assertThat(parametros.get(3).contaControle()).isEqualTo(new ContaContabilId(empenhadoPago));
        for (ParametroEncerramentoOrcamentario parametro : parametros) {
            assertThat(parametro.contaContrapartida()).isEqualTo(new ContaContabilId(creditoInicial));
            assertThat(parametro.naturezaContrapartida()).isEqualTo(Natureza.CREDITO);
        }
    }

    @Test
    @DisplayName("falha fechado quando o Crédito Inicial não está provisionado no ente")
    void falhaQuandoContrapartidaNaoExiste() {
        when(jdbcTemplate.query(any(String.class), anyRowMapper(), eq(enteId.valor()), eq("5.2.2.1.1.01.00")))
                .thenReturn(List.of());

        assertThatIllegalStateException()
                .isThrownBy(() -> new ParametrosEncerramentoOrcamentarioOficiais(jdbcTemplate).para(enteId))
                .withMessageContaining("5.2.2.1.1.01.00");
    }

    @Test
    @DisplayName("falha fechado quando um controle de execução da despesa não está provisionado no ente")
    void falhaQuandoControleNaoExiste() {
        mockConta("5.2.2.1.1.01.00", UUID.randomUUID());
        when(jdbcTemplate.query(any(String.class), anyRowMapper(), eq(enteId.valor()), eq("6.2.2.1.1")))
                .thenReturn(List.of());

        assertThatIllegalStateException()
                .isThrownBy(() -> new ParametrosEncerramentoOrcamentarioOficiais(jdbcTemplate).para(enteId))
                .withMessageContaining("6.2.2.1.1");
    }

    private void mockConta(String codigo, UUID id) {
        when(jdbcTemplate.query(any(String.class), anyRowMapper(), eq(enteId.valor()), eq(codigo)))
                .thenReturn(List.of(id));
    }

    private RowMapper<UUID> anyRowMapper() {
        return any();
    }
}
