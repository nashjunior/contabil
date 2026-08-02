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
import br.contabil.razao.domain.ContaContabilId;

@ExtendWith(MockitoExtension.class)
class ContaResultadoApuradoOficialTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());

    @Test
    @DisplayName("RAZ-270: resolve o código PCASP oficial de resultado patrimonial")
    void resolveCodigoPcaspOficial() {
        UUID contaResultado = UUID.randomUUID();
        mockConta("2.3.7.1.1.01.00", contaResultado);

        ContaContabilId resolvida = new ContaResultadoApuradoOficial(jdbcTemplate).para(enteId);

        assertThat(resolvida).isEqualTo(new ContaContabilId(contaResultado));
    }

    @Test
    @DisplayName("falha fechado quando a conta de resultado patrimonial não está provisionada no ente")
    void falhaQuandoContaObrigatoriaNaoExiste() {
        when(jdbcTemplate.query(any(String.class), anyRowMapper(), eq(enteId.valor()), eq("2.3.7.1.1.01.00")))
                .thenReturn(List.of());

        assertThatIllegalStateException()
                .isThrownBy(() -> new ContaResultadoApuradoOficial(jdbcTemplate).para(enteId))
                .withMessageContaining("2.3.7.1.1.01.00");
    }

    private void mockConta(String codigo, UUID id) {
        when(jdbcTemplate.query(any(String.class), anyRowMapper(), eq(enteId.valor()), eq(codigo)))
                .thenReturn(List.of(id));
    }

    private RowMapper<UUID> anyRowMapper() {
        return any();
    }
}
