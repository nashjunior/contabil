package br.contabil.razao.infra;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.application.ParametroInscricaoRP;
import br.contabil.razao.application.ParametrosInscricaoRP;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.Natureza;

/**
 * Parâmetros oficiais de inscrição de RP centralizados na borda.
 *
 * <p>Origem = contas de execução da despesa já escrituradas no exercício; destino =
 * contas PCASP de execução de RP. A resolução é por {@code ente_id}, pois
 * {@code conta_pcasp} é tenant-scoped.
 */
final class ParametrosInscricaoRPOficiais implements ParametrosInscricaoRP {

    private static final String CODIGO_EMPENHADO_A_LIQUIDAR = "6.2.2.1.3";
    private static final String CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR = "6.2.2.1.4";
    private static final String CODIGO_RPNP_A_LIQUIDAR = "6.3.1.1";
    private static final String CODIGO_RPP_A_PAGAR = "6.3.2.1";

    private static final String SQL_RESOLVER_CONTA = "select id from conta_pcasp where ente_id = ? and codigo = ?";

    private final JdbcTemplate jdbcTemplate;

    ParametrosInscricaoRPOficiais(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public List<ParametroInscricaoRP> para(TenantId enteId) {
        Objects.requireNonNull(enteId, "enteId");

        return List.of(
                new ParametroInscricaoRP(
                        resolverConta(enteId, CODIGO_EMPENHADO_A_LIQUIDAR),
                        resolverConta(enteId, CODIGO_RPNP_A_LIQUIDAR),
                        Natureza.CREDITO),
                new ParametroInscricaoRP(
                        resolverConta(enteId, CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR),
                        resolverConta(enteId, CODIGO_RPP_A_PAGAR),
                        Natureza.CREDITO));
    }

    private ContaContabilId resolverConta(TenantId enteId, String codigoPcasp) {
        List<UUID> ids = jdbcTemplate.query(
                SQL_RESOLVER_CONTA, (rs, rowNum) -> rs.getObject("id", UUID.class), enteId.valor(), codigoPcasp);
        if (ids.isEmpty()) {
            throw new IllegalStateException(
                    "Conta PCASP obrigatória para inscrição de RP não cadastrada: " + codigoPcasp);
        }
        return new ContaContabilId(ids.get(0));
    }
}
