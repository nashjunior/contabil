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
import br.contabil.razao.application.ParametroTransposicaoDdrAbertura;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.Natureza;

@ExtendWith(MockitoExtension.class)
class ParametrosTransposicaoDdrAberturaOficiaisTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());

    @Test
    @DisplayName("RAZ-266: resolve os códigos PCASP oficiais de recursos disponíveis e de exercícios anteriores")
    void resolveCodigosPcaspOficiais() {
        UUID recursosDisponiveis = UUID.randomUUID();
        UUID recursosExerciciosAnteriores = UUID.randomUUID();

        mockConta("8.2.1.1.1.01.00", recursosDisponiveis);
        mockConta("8.2.1.1.1.02.00", recursosExerciciosAnteriores);

        List<ParametroTransposicaoDdrAbertura> parametros =
                new ParametrosTransposicaoDdrAberturaOficiais(jdbcTemplate).para(enteId);

        assertThat(parametros).hasSize(1);
        assertThat(parametros.get(0).contaOrigem()).isEqualTo(new ContaContabilId(recursosDisponiveis));
        assertThat(parametros.get(0).contaDestino()).isEqualTo(new ContaContabilId(recursosExerciciosAnteriores));
        assertThat(parametros.get(0).naturezaDestino()).isEqualTo(Natureza.CREDITO);
    }

    @Test
    @DisplayName("falha fechado quando conta PCASP obrigatória de abertura da DDR não está provisionada no ente")
    void falhaQuandoContaObrigatoriaNaoExiste() {
        when(jdbcTemplate.query(
                any(String.class),
                anyRowMapper(),
                eq(enteId.valor()),
                eq("8.2.1.1.1.01.00")))
                .thenReturn(List.of());

        assertThatIllegalStateException()
                .isThrownBy(() -> new ParametrosTransposicaoDdrAberturaOficiais(jdbcTemplate).para(enteId))
                .withMessageContaining("8.2.1.1.1.01.00");
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
