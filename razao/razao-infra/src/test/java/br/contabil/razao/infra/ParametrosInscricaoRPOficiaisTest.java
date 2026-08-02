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
import br.contabil.razao.application.ParametroInscricaoRP;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.Natureza;

@ExtendWith(MockitoExtension.class)
class ParametrosInscricaoRPOficiaisTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());

    @Test
    @DisplayName("RAZ-260: resolve os quatro códigos PCASP oficiais e monta RPNP/RPP")
    void resolveCodigosPcaspOficiais() {
        UUID empenhadoALiquidar = UUID.randomUUID();
        UUID rpnpaLiquidar = UUID.randomUUID();
        UUID liquidadoAPagar = UUID.randomUUID();
        UUID rppAPagar = UUID.randomUUID();

        mockConta("6.2.2.1.3", empenhadoALiquidar);
        mockConta("6.3.1.1", rpnpaLiquidar);
        mockConta("6.2.2.1.4", liquidadoAPagar);
        mockConta("6.3.2.1", rppAPagar);

        List<ParametroInscricaoRP> parametros = new ParametrosInscricaoRPOficiais(jdbcTemplate).para(enteId);

        assertThat(parametros).hasSize(2);
        assertThat(parametros.get(0).contaOrigem()).isEqualTo(new ContaContabilId(empenhadoALiquidar));
        assertThat(parametros.get(0).contaDestino()).isEqualTo(new ContaContabilId(rpnpaLiquidar));
        assertThat(parametros.get(0).naturezaDestino()).isEqualTo(Natureza.CREDITO);
        assertThat(parametros.get(1).contaOrigem()).isEqualTo(new ContaContabilId(liquidadoAPagar));
        assertThat(parametros.get(1).contaDestino()).isEqualTo(new ContaContabilId(rppAPagar));
        assertThat(parametros.get(1).naturezaDestino()).isEqualTo(Natureza.CREDITO);
    }

    @Test
    @DisplayName("falha fechado quando conta PCASP obrigatória de RP não está provisionada no ente")
    void falhaQuandoContaObrigatoriaNaoExiste() {
        when(jdbcTemplate.query(
                any(String.class),
                anyRowMapper(),
                eq(enteId.valor()),
                eq("6.2.2.1.3")))
                .thenReturn(List.of());

        assertThatIllegalStateException()
                .isThrownBy(() -> new ParametrosInscricaoRPOficiais(jdbcTemplate).para(enteId))
                .withMessageContaining("6.2.2.1.3");
    }

    private void mockConta(String codigo, UUID id) {
        when(jdbcTemplate.query(
                any(String.class),
                anyRowMapper(),
                eq(enteId.valor()),
                eq(codigo)))
                .thenReturn(List.of(id));
    }

    private RowMapper<UUID> anyRowMapper() {
        return any();
    }
}
