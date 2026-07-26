package br.contabil.execucao.infra;

import br.contabil.execucao.domain.ContratoId;
import br.contabil.execucao.domain.CredorId;
import br.contabil.execucao.domain.DocumentoAssinadoEmpenho;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.StatusEmpenho;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelAssinatura;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
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
            select e.id, e.numero_sequencial, e.exercicio, e.tipo, e.dotacao_id, e.credor_id,
                   e.unidade_gestora_id, e.contrato_id, e.valor, e.data_fato, e.classificacao_orcamentaria,
                   e.fonte_recurso, e.historico, e.fato_contabil_id, e.autor_cpf, e.status,
                   e.documento_pendente_uri, d.uri_pdf_assinado, d.hash_sha256, d.manifesto,
                   d.transacao_id, d.nivel_assinatura, d.signatario_cpf, d.assinado_em
              from empenho e
              left join execucao_empenho_documento_assinado d
                     on d.ente_id = e.ente_id and d.empenho_id = e.id
             where e.ente_id = ? and e.id = ?
            """;

    private static final String SQL_MARCAR_PENDENTE_ASSINATURA =
            """
            update empenho
               set status = 'pendente_assinatura', documento_pendente_uri = ?
             where ente_id = ? and id = ? and status = 'registrado'
            """;

    private static final String SQL_MARCAR_ASSINADO =
            """
            update empenho
               set status = 'assinado'
             where ente_id = ? and id = ? and status in ('pendente_assinatura', 'assinatura_rejeitada')
            """;

    private static final String SQL_INSERIR_DOCUMENTO_ASSINADO =
            """
            insert into execucao_empenho_documento_assinado
                (ente_id, empenho_id, uri_pdf_assinado, hash_sha256, manifesto, transacao_id,
                 nivel_assinatura, signatario_cpf, assinado_em)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_MARCAR_ASSINATURA_REJEITADA =
            """
            update empenho
               set status = 'assinatura_rejeitada'
             where ente_id = ? and id = ? and status = 'pendente_assinatura'
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
                empenho.fatoContabilId().valor(),
                empenho.autor().numero());
    }

    @Override
    public Optional<Empenho> buscarPorId(TenantId enteId, EmpenhoId id) {
        List<Empenho> linhas =
                jdbcTemplate.query(SQL_BUSCAR, (rs, rowNum) -> mapear(enteId, rs), enteId.valor(), id.valor());
        return linhas.stream().findFirst();
    }

    @Override
    public boolean marcarPendenteAssinatura(TenantId enteId, EmpenhoId id, URI documentoPendenteUri) {
        int atualizadas = jdbcTemplate.update(
                SQL_MARCAR_PENDENTE_ASSINATURA, documentoPendenteUri.toString(), enteId.valor(), id.valor());
        return atualizadas > 0;
    }

    @Override
    public boolean marcarAssinado(TenantId enteId, EmpenhoId id, DocumentoAssinadoEmpenho documentoAssinado) {
        int atualizadas = jdbcTemplate.update(SQL_MARCAR_ASSINADO, enteId.valor(), id.valor());
        if (atualizadas == 0) {
            return false;
        }
        jdbcTemplate.update(
                SQL_INSERIR_DOCUMENTO_ASSINADO,
                enteId.valor(),
                id.valor(),
                documentoAssinado.pdfAssinado().toString(),
                documentoAssinado.hashSha256(),
                documentoAssinado.manifesto(),
                documentoAssinado.idTransacao(),
                documentoAssinado.nivel().name(),
                documentoAssinado.signatario().numero(),
                Timestamp.from(documentoAssinado.assinadoEm()));
        return true;
    }

    @Override
    public boolean marcarAssinaturaRejeitada(TenantId enteId, EmpenhoId id) {
        int atualizadas = jdbcTemplate.update(SQL_MARCAR_ASSINATURA_REJEITADA, enteId.valor(), id.valor());
        return atualizadas > 0;
    }

    private static Empenho mapear(TenantId enteId, ResultSet rs) throws SQLException {
        UUID contratoIdRaw = rs.getObject("contrato_id", UUID.class);
        String documentoPendenteUriRaw = rs.getString("documento_pendente_uri");
        return Empenho.reconstituir(
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
                new ReferenciaFatoContabil(rs.getObject("fato_contabil_id", UUID.class)),
                new Cpf(rs.getString("autor_cpf")),
                StatusEmpenho.valueOf(rs.getString("status").toUpperCase(Locale.ROOT)),
                documentoPendenteUriRaw == null ? Optional.empty() : Optional.of(URI.create(documentoPendenteUriRaw)),
                mapearDocumentoAssinado(rs));
    }

    private static Optional<DocumentoAssinadoEmpenho> mapearDocumentoAssinado(ResultSet rs) throws SQLException {
        String uriPdfAssinado = rs.getString("uri_pdf_assinado");
        if (uriPdfAssinado == null) {
            return Optional.empty();
        }
        return Optional.of(new DocumentoAssinadoEmpenho(
                URI.create(uriPdfAssinado),
                rs.getString("hash_sha256"),
                rs.getString("manifesto"),
                rs.getObject("transacao_id", UUID.class),
                NivelAssinatura.valueOf(rs.getString("nivel_assinatura")),
                new Cpf(rs.getString("signatario_cpf")),
                rs.getTimestamp("assinado_em").toInstant()));
    }
}
