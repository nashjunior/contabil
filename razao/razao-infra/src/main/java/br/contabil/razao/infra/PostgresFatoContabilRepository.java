package br.contabil.razao.infra;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.FatoContabilId;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.LancamentoId;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.FatoContabilRepository;

/**
 * Adapter Postgres do razão. {@link #inserir(FatoContabil)} grava o fato e
 * TODOS os lançamentos numa única transação — o chamador (use case) já está
 * dentro de {@code @Transactional}; o batch insert dos lançamentos é atômico,
 * NÃO fail-soft (ADR-0013 não se aplica ao razão). O constraint trigger
 * diferido do banco reconfirma Σdébito=Σcrédito no commit, como defesa em
 * profundidade (razao-contabil-schema.md).
 *
 * <p>Sem {@code atualizar}/{@code excluir}: só implementa o {@link FatoContabilRepository},
 * que não declara essas operações (append-only, guardiao-arquitetura/ArchUnit).
 */
@Repository
public class PostgresFatoContabilRepository implements FatoContabilRepository {

    private static final String SQL_INSERT_FATO =
            """
            insert into fato_contabil
                (id, ente_id, numero_seq, data_competencia, data_hora_registro, periodo_id,
                 tipo_evento, historico, origem, fato_estornado_id)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_INSERT_LANCAMENTO =
            """
            insert into lancamento (id, ente_id, fato_id, conta_id, natureza, valor)
            values (?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_BUSCAR_FATO =
            "select id, numero_seq, data_competencia, data_hora_registro, periodo_id, "
                    + "tipo_evento, historico, origem, fato_estornado_id "
                    + "from fato_contabil where ente_id = ? and id = ?";

    private static final String SQL_BUSCAR_LANCAMENTOS =
            "select id, conta_id, natureza, valor from lancamento where ente_id = ? and fato_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public PostgresFatoContabilRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void inserir(FatoContabil fato) {
        jdbcTemplate.update(
                SQL_INSERT_FATO,
                fato.id().valor(),
                fato.enteId().valor(),
                fato.numeroSeq(),
                fato.dataCompetencia(),
                fato.dataHoraRegistro(),
                fato.periodoId().valor(),
                fato.tipoEvento().codigo(),
                fato.historico(),
                fato.origem(),
                fato.fatoEstornadoId() == null ? null : fato.fatoEstornadoId().valor());

        List<Lancamento> lancamentos = fato.lancamentos();
        jdbcTemplate.batchUpdate(
                SQL_INSERT_LANCAMENTO,
                lancamentos,
                lancamentos.size(),
                (PreparedStatement ps, Lancamento lancamento) -> {
                    ps.setObject(1, lancamento.id().valor());
                    ps.setObject(2, fato.enteId().valor());
                    ps.setObject(3, fato.id().valor());
                    ps.setObject(4, lancamento.contaId().valor());
                    ps.setString(5, String.valueOf(lancamento.natureza().codigo()));
                    ps.setBigDecimal(6, lancamento.valor().valor());
                });
    }

    @Override
    public Optional<FatoContabil> buscarPorId(TenantId enteId, FatoContabilId id) {
        List<LinhaFato> linhas =
                jdbcTemplate.query(SQL_BUSCAR_FATO, (rs, rowNum) -> mapearLinhaFato(rs), enteId.valor(), id.valor());
        if (linhas.isEmpty()) {
            return Optional.empty();
        }

        List<Lancamento> lancamentos = jdbcTemplate.query(
                SQL_BUSCAR_LANCAMENTOS, (rs, rowNum) -> mapearLancamento(rs), enteId.valor(), id.valor());

        LinhaFato linha = linhas.get(0);
        return Optional.of(FatoContabil.reconstituir(
                linha.id(),
                enteId,
                linha.numeroSeq(),
                linha.dataCompetencia(),
                linha.dataHoraRegistro(),
                linha.periodoId(),
                linha.tipoEvento(),
                linha.historico(),
                linha.origem(),
                linha.fatoEstornadoId(),
                lancamentos));
    }

    private static LinhaFato mapearLinhaFato(ResultSet rs) throws SQLException {
        return new LinhaFato(
                new FatoContabilId(rs.getObject("id", UUID.class)),
                rs.getLong("numero_seq"),
                rs.getDate("data_competencia").toLocalDate(),
                rs.getTimestamp("data_hora_registro").toLocalDateTime(),
                new PeriodoContabilId(rs.getObject("periodo_id", UUID.class)),
                TipoEvento.deCodigo(rs.getString("tipo_evento")),
                rs.getString("historico"),
                rs.getString("origem"),
                fatoContabilId(rs.getObject("fato_estornado_id", UUID.class)));
    }

    private static Lancamento mapearLancamento(ResultSet rs) throws SQLException {
        return Lancamento.reconstituir(
                new LancamentoId(rs.getObject("id", UUID.class)),
                new ContaContabilId(rs.getObject("conta_id", UUID.class)),
                Natureza.deCodigo(rs.getString("natureza").charAt(0)),
                new Dinheiro(rs.getBigDecimal("valor")));
    }

    private static FatoContabilId fatoContabilId(UUID id) {
        return id == null ? null : new FatoContabilId(id);
    }

    private record LinhaFato(
            FatoContabilId id,
            long numeroSeq,
            LocalDate dataCompetencia,
            LocalDateTime dataHoraRegistro,
            PeriodoContabilId periodoId,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            FatoContabilId fatoEstornadoId) {}
}
