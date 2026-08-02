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
import br.contabil.razao.application.ParametroEncerramentoDdr;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.Natureza;

@ExtendWith(MockitoExtension.class)
class ParametrosEncerramentoDdrOficiaisTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());

    @Test
    @DisplayName("RAZ-266: resolve os códigos PCASP oficiais da DDR utilizada e do controle de disponibilidade")
    void resolveCodigosPcaspOficiais() {
        UUID ddrUtilizada = UUID.randomUUID();
        UUID controleDisponibilidade = UUID.randomUUID();

        mockConta("8.2.1.1.4.00.00", ddrUtilizada);
        mockConta("7.2.1.1.1.00.00", controleDisponibilidade);

        List<ParametroEncerramentoDdr> parametros = new ParametrosEncerramentoDdrOficiais(jdbcTemplate).para(enteId);

        assertThat(parametros).hasSize(1);
        assertThat(parametros.get(0).contaOrigem()).isEqualTo(new ContaContabilId(ddrUtilizada));
        assertThat(parametros.get(0).contaDestino()).isEqualTo(new ContaContabilId(controleDisponibilidade));
        assertThat(parametros.get(0).naturezaDestino()).isEqualTo(Natureza.CREDITO);
    }

    @Test
    @DisplayName("falha fechado quando conta PCASP obrigatória da DDR não está provisionada no ente")
    void falhaQuandoContaObrigatoriaNaoExiste() {
        when(jdbcTemplate.query(
                any(String.class),
                anyRowMapper(),
                eq(enteId.valor()),
                eq("8.2.1.1.4.00.00")))
                .thenReturn(List.of());

        assertThatIllegalStateException()
                .isThrownBy(() -> new ParametrosEncerramentoDdrOficiais(jdbcTemplate).para(enteId))
                .withMessageContaining("8.2.1.1.4.00.00");
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
