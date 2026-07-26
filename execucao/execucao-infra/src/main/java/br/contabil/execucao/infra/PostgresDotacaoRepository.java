package br.contabil.execucao.infra;

import br.contabil.execucao.domain.Dotacao;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.repository.DotacaoRepository;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Adapter Postgres da dotação (JdbcTemplate — mesma escolha do empenho/razão, sem JPA). */
@Repository
public class PostgresDotacaoRepository implements DotacaoRepository {

    private static final String SQL_INSERT =
            """
            insert into dotacao
                (id, ente_id, exercicio, classificacao_orcamentaria, fonte_recurso,
                 unidade_gestora_id, valor_autorizado)
            values (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_BUSCAR =
            """
            select id, exercicio, classificacao_orcamentaria, fonte_recurso, unidade_gestora_id, valor_autorizado
              from dotacao
             where ente_id = ? and id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresDotacaoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void inserir(Dotacao dotacao) {
        jdbcTemplate.update(
                SQL_INSERT,
                dotacao.id().valor(),
                dotacao.enteId().valor(),
                dotacao.exercicio(),
                dotacao.classificacaoOrcamentaria(),
                dotacao.fonteRecurso(),
                dotacao.unidadeGestoraId(),
                dotacao.valorAutorizado().valor());
    }

    @Override
    public Optional<Dotacao> buscarPorId(TenantId enteId, DotacaoId id) {
        List<Dotacao> linhas =
                jdbcTemplate.query(SQL_BUSCAR, (rs, rowNum) -> mapear(enteId, rs), enteId.valor(), id.valor());
        return linhas.stream().findFirst();
    }

    private static Dotacao mapear(TenantId enteId, ResultSet rs) throws SQLException {
        return Dotacao.registrar(
                new DotacaoId(rs.getObject("id", UUID.class)),
                enteId,
                rs.getInt("exercicio"),
                rs.getString("classificacao_orcamentaria"),
                rs.getString("fonte_recurso"),
                rs.getObject("unidade_gestora_id", UUID.class),
                new Dinheiro(rs.getBigDecimal("valor_autorizado")));
    }
}
