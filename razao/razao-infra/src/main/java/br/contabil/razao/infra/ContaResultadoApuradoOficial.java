package br.contabil.razao.infra;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.application.ContaResultadoApurado;
import br.contabil.razao.domain.ContaContabilId;

/**
 * Conta oficial de resultado patrimonial centralizada na borda — RAZ-270 (mesmo padrão
 * do RAZ-260/RAZ-266).
 *
 * <p>Apura o resultado do exercício (VPA/VPD classes 3/4) diretamente contra a conta de
 * Patrimônio Líquido {@code 2.3.7.1.1.01.00} — Superávits ou Déficits do Exercício, sem
 * conta transitória intermediária (IPC 03/STN rev. 2017, itens 21/24, docs/15
 * §Fontes/a revalidar). A resolução é por {@code ente_id}, pois {@code conta_pcasp} é
 * tenant-scoped.
 */
final class ContaResultadoApuradoOficial implements ContaResultadoApurado {

    private static final String CODIGO_RESULTADO_APURADO = "2.3.7.1.1.01.00";

    private static final String SQL_RESOLVER_CONTA = "select id from conta_pcasp where ente_id = ? and codigo = ?";

    private final JdbcTemplate jdbcTemplate;

    ContaResultadoApuradoOficial(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public ContaContabilId para(TenantId enteId) {
        Objects.requireNonNull(enteId, "enteId");

        List<UUID> ids = jdbcTemplate.query(
                SQL_RESOLVER_CONTA,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                enteId.valor(),
                CODIGO_RESULTADO_APURADO);
        if (ids.isEmpty()) {
            throw new IllegalStateException(
                    "Conta PCASP obrigatória para apuração do resultado patrimonial não cadastrada: "
                            + CODIGO_RESULTADO_APURADO);
        }
        return new ContaContabilId(ids.get(0));
    }
}
