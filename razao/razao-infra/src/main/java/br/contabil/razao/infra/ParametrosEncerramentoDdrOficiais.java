package br.contabil.razao.infra;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.application.ParametroEncerramentoDdr;
import br.contabil.razao.application.ParametrosEncerramentoDdr;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.Natureza;

/**
 * Parâmetro oficial de encerramento da DDR (classes 7/8) centralizado na borda —
 * RAZ-266 (mesmo padrão do RAZ-260 para RP).
 *
 * <p>Encerra apenas a disponibilidade <b>utilizada</b> (docs/15-fechamento-contabil.md
 * §Preciso item 4, docs/17-restos-a-pagar.md §Preciso item 2, IPC 03/STN rev. 2017 §91):
 * origem {@code 8.2.1.1.4.00.00} (DDR Utilizada), destino {@code 7.2.1.1.1.00.00}
 * (Controle da Disponibilidade de Recursos). A quebra por fonte não é por conta PCASP —
 * é a dimensão {@code fonte_recurso} do lançamento (ADR-0054: FR é código único, não
 * hierarquia de contas) — por isso um único par origem/destino cobre todas as fontes do
 * ente. A resolução é por {@code ente_id}, pois {@code conta_pcasp} é tenant-scoped.
 * {@code [REVALIDAR]} os códigos exatos contra o MCASP edição vigente antes de produção.
 */
final class ParametrosEncerramentoDdrOficiais implements ParametrosEncerramentoDdr {

    private static final String CODIGO_DDR_UTILIZADA = "8.2.1.1.4.00.00";
    private static final String CODIGO_CONTROLE_DISPONIBILIDADE = "7.2.1.1.1.00.00";

    private static final String SQL_RESOLVER_CONTA = "select id from conta_pcasp where ente_id = ? and codigo = ?";

    private final JdbcTemplate jdbcTemplate;

    ParametrosEncerramentoDdrOficiais(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public List<ParametroEncerramentoDdr> para(TenantId enteId) {
        Objects.requireNonNull(enteId, "enteId");

        return List.of(new ParametroEncerramentoDdr(
                resolverConta(enteId, CODIGO_DDR_UTILIZADA),
                resolverConta(enteId, CODIGO_CONTROLE_DISPONIBILIDADE),
                Natureza.CREDITO));
    }

    private ContaContabilId resolverConta(TenantId enteId, String codigoPcasp) {
        List<UUID> ids = jdbcTemplate.query(
                SQL_RESOLVER_CONTA, (rs, rowNum) -> rs.getObject("id", UUID.class), enteId.valor(), codigoPcasp);
        if (ids.isEmpty()) {
            throw new IllegalStateException(
                    "Conta PCASP obrigatória para encerramento da DDR não cadastrada: " + codigoPcasp);
        }
        return new ContaContabilId(ids.get(0));
    }
}
