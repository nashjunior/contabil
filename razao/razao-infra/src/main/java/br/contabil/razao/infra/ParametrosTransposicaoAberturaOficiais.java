package br.contabil.razao.infra;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.application.ParametroTransposicaoAbertura;
import br.contabil.razao.application.ParametrosTransposicaoAbertura;
import br.contabil.razao.domain.ContaContabilId;

/**
 * Parâmetro oficial de transposição patrimonial na abertura do exercício seguinte
 * centralizado na borda — RAZ-270 (mesmo padrão do RAZ-260/RAZ-266).
 *
 * <p>Transpõe o saldo da conta de resultado do exercício encerrado para a conta de
 * resultados de exercícios anteriores (IPC 03/STN rev. 2017, item 30, p. 11; docs/15
 * §Fontes/a revalidar): origem {@code 2.3.7.1.1.01.00} (Superávits ou Déficits do
 * Exercício), destino {@code 2.3.7.1.1.02.00} (Superávits ou Déficits de Exercícios
 * Anteriores). A resolução é por {@code ente_id}, pois {@code conta_pcasp} é
 * tenant-scoped.
 */
final class ParametrosTransposicaoAberturaOficiais implements ParametrosTransposicaoAbertura {

    private static final String CODIGO_RESULTADO_EXERCICIO = "2.3.7.1.1.01.00";
    private static final String CODIGO_RESULTADO_EXERCICIOS_ANTERIORES = "2.3.7.1.1.02.00";

    private static final String SQL_RESOLVER_CONTA = "select id from conta_pcasp where ente_id = ? and codigo = ?";

    private final JdbcTemplate jdbcTemplate;

    ParametrosTransposicaoAberturaOficiais(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public List<ParametroTransposicaoAbertura> para(TenantId enteId) {
        Objects.requireNonNull(enteId, "enteId");

        return List.of(new ParametroTransposicaoAbertura(
                resolverConta(enteId, CODIGO_RESULTADO_EXERCICIO),
                resolverConta(enteId, CODIGO_RESULTADO_EXERCICIOS_ANTERIORES)));
    }

    private ContaContabilId resolverConta(TenantId enteId, String codigoPcasp) {
        List<UUID> ids = jdbcTemplate.query(
                SQL_RESOLVER_CONTA, (rs, rowNum) -> rs.getObject("id", UUID.class), enteId.valor(), codigoPcasp);
        if (ids.isEmpty()) {
            throw new IllegalStateException(
                    "Conta PCASP obrigatória para abertura do exercício não cadastrada: " + codigoPcasp);
        }
        return new ContaContabilId(ids.get(0));
    }
}
