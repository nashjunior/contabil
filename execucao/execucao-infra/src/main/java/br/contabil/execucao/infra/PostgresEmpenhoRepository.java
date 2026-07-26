package br.contabil.execucao.infra;

import br.contabil.execucao.domain.ContratoId;
import br.contabil.execucao.domain.CredorId;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Adapter Postgres do empenho (JdbcTemplate — mesma escolha do razão, sem JPA). */
@Repository
public class PostgresEmpenhoRepository implements EmpenhoRepository {

    private static final String SQL_INSERT =
            """
            insert into empenho
                (id, ente_id, numero_sequencial, exercicio, tipo, dotacao_id, credor_id,
                 unidade_gestora_id, contrato_id, valor, data_fato, classificacao_orcamentaria,
                 fonte_recurso, historico, fato_contabil_id, autor_cpf)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_BUSCAR =
            """
            select id, numero_sequencial, exercicio, tipo, dotacao_id, credor_id, unidade_gestora_id,
                   contrato_id, valor, data_fato, classificacao_orcamentaria, fonte_recurso, historico,
                   fato_contabil_id, autor_cpf
              from empenho
             where ente_id = ? and id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresEmpenhoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void inserir(Empenho empenho) {
        jdbcTemplate.update(
                SQL_INSERT,
                empenho.id().valor(),
                empenho.enteId().valor(),
                empenho.numeroSequencial(),
                empenho.exercicio(),
                empenho.tipo().codigo(),
                empenho.dotacaoId().valor(),
                empenho.credorId().valor(),
                empenho.unidadeGestoraId().valor(),
                empenho.contratoId() == null ? null : empenho.contratoId().valor(),
                empenho.valor().valor(),
                empenho.dataFato(),
                empenho.classificacaoOrcamentaria(),
                empenho.fonteRecurso(),
                empenho.historico(),
                empenho.fatoContabilId(),
                empenho.autor().numero());
    }

    @Override
    public Optional<Empenho> buscarPorId(TenantId enteId, EmpenhoId id) {
        List<Empenho> linhas =
                jdbcTemplate.query(SQL_BUSCAR, (rs, rowNum) -> mapear(enteId, rs), enteId.valor(), id.valor());
        return linhas.stream().findFirst();
    }

    private static Empenho mapear(TenantId enteId, ResultSet rs) throws SQLException {
        UUID contratoIdRaw = rs.getObject("contrato_id", UUID.class);
        return Empenho.registrar(
                new EmpenhoId(rs.getObject("id", UUID.class)),
                enteId,
                rs.getLong("numero_sequencial"),
                rs.getInt("exercicio"),
                TipoEmpenho.deCodigo(rs.getString("tipo")),
                new DotacaoId(rs.getObject("dotacao_id", UUID.class)),
                new CredorId(rs.getObject("credor_id", UUID.class)),
                new UnidadeGestoraId(rs.getObject("unidade_gestora_id", UUID.class)),
                contratoIdRaw == null ? null : new ContratoId(contratoIdRaw),
                new Dinheiro(rs.getBigDecimal("valor")),
                rs.getDate("data_fato").toLocalDate(),
                rs.getString("classificacao_orcamentaria"),
                rs.getString("fonte_recurso"),
                rs.getString("historico"),
                rs.getObject("fato_contabil_id", UUID.class),
                new Cpf(rs.getString("autor_cpf")));
    }
}
