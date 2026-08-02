package br.contabil.razao.infra;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.application.ParametroTransposicaoDdrAbertura;
import br.contabil.razao.application.ParametrosTransposicaoDdrAbertura;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.Natureza;

/**
 * Parâmetro oficial de transposição da DDR (classes 7/8) por fonte na abertura do
 * exercício seguinte, centralizado na borda — RAZ-266 (mesmo padrão do RAZ-260 para RP).
 *
 * <p>Transpõe o superávit financeiro por fonte (docs/15-fechamento-contabil.md §Preciso
 * item 5, docs/17-restos-a-pagar.md §Preciso item 2, IPC 03/STN rev. 2017 §96): origem
 * {@code 8.2.1.1.1.01.00} (Recursos Disponíveis para o Exercício), destino
 * {@code 8.2.1.1.1.02.00} (Recursos de Exercícios Anteriores). Um único par origem/destino
 * cobre todas as fontes do ente — a fonte é a dimensão {@code fonte_recurso} do lançamento
 * (ADR-0054), não uma conta PCASP por fonte. A resolução é por {@code ente_id}, pois
 * {@code conta_pcasp} é tenant-scoped. {@code [REVALIDAR]} os códigos exatos contra o
 * MCASP edição vigente antes de produção.
 */
final class ParametrosTransposicaoDdrAberturaOficiais implements ParametrosTransposicaoDdrAbertura {

    private static final String CODIGO_RECURSOS_DISPONIVEIS_EXERCICIO = "8.2.1.1.1.01.00";
    private static final String CODIGO_RECURSOS_EXERCICIOS_ANTERIORES = "8.2.1.1.1.02.00";

    private static final String SQL_RESOLVER_CONTA = "select id from conta_pcasp where ente_id = ? and codigo = ?";

    private final JdbcTemplate jdbcTemplate;

    ParametrosTransposicaoDdrAberturaOficiais(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public List<ParametroTransposicaoDdrAbertura> para(TenantId enteId) {
        Objects.requireNonNull(enteId, "enteId");

        return List.of(new ParametroTransposicaoDdrAbertura(
                resolverConta(enteId, CODIGO_RECURSOS_DISPONIVEIS_EXERCICIO),
                resolverConta(enteId, CODIGO_RECURSOS_EXERCICIOS_ANTERIORES),
                Natureza.CREDITO));
    }

    private ContaContabilId resolverConta(TenantId enteId, String codigoPcasp) {
        List<UUID> ids = jdbcTemplate.query(
                SQL_RESOLVER_CONTA, (rs, rowNum) -> rs.getObject("id", UUID.class), enteId.valor(), codigoPcasp);
        if (ids.isEmpty()) {
            throw new IllegalStateException(
                    "Conta PCASP obrigatória para abertura da DDR não cadastrada: " + codigoPcasp);
        }
        return new ContaContabilId(ids.get(0));
    }
}
