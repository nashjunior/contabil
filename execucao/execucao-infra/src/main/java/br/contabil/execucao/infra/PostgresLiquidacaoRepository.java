package br.contabil.execucao.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.contabil.execucao.domain.DocumentoSuporte;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.StatusAprovacao;
import br.contabil.execucao.domain.repository.LiquidacaoRepository;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;

/** Adapter Postgres da liquidação (JdbcTemplate — mesma escolha do empenho, sem JPA). */
@Repository
public class PostgresLiquidacaoRepository implements LiquidacaoRepository {

    private static final TypeReference<List<DocumentoSuporteJson>> DOCUMENTOS_TYPE = new TypeReference<>() {};

    private static final String SQL_INSERT =
            """
            insert into liquidacao
                (id, ente_id, empenho_id, data_competencia, valor, documentos_suporte, historico,
                 fato_contabil_id, autor_cpf, status_aprovacao, aprovador_cpf, motivo_devolucao)
            values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_BUSCAR =
            """
            select id, empenho_id, data_competencia, valor, documentos_suporte, historico,
                   fato_contabil_id, autor_cpf, status_aprovacao, aprovador_cpf, motivo_devolucao
              from liquidacao
             where ente_id = ? and id = ?
            """;

    private static final String SQL_ATUALIZAR_DECISAO_APROVACAO =
            """
            update liquidacao
               set status_aprovacao = ?, aprovador_cpf = ?, motivo_devolucao = ?
             where ente_id = ? and id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public PostgresLiquidacaoRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new ObjectMapper());
    }

    PostgresLiquidacaoRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void inserir(Liquidacao liquidacao) {
        jdbcTemplate.update(
                SQL_INSERT,
                liquidacao.id().valor(),
                liquidacao.enteId().valor(),
                liquidacao.empenhoId().valor(),
                liquidacao.dataCompetencia(),
                liquidacao.valor().valor(),
                escreverDocumentos(liquidacao.documentosSuporte()),
                liquidacao.historico(),
                liquidacao.fatoContabilId(),
                liquidacao.autor().numero(),
                codigoStatus(liquidacao.statusAprovacao()),
                liquidacao.aprovador().map(Cpf::numero).orElse(null),
                liquidacao.motivoDevolucao().orElse(null));
    }

    @Override
    public Optional<Liquidacao> buscarPorId(TenantId enteId, LiquidacaoId id) {
        List<Liquidacao> linhas =
                jdbcTemplate.query(SQL_BUSCAR, (rs, rowNum) -> mapear(enteId, rs), enteId.valor(), id.valor());
        return linhas.stream().findFirst();
    }

    @Override
    public void atualizarDecisaoAprovacao(Liquidacao liquidacao) {
        jdbcTemplate.update(
                SQL_ATUALIZAR_DECISAO_APROVACAO,
                codigoStatus(liquidacao.statusAprovacao()),
                liquidacao.aprovador().map(Cpf::numero).orElse(null),
                liquidacao.motivoDevolucao().orElse(null),
                liquidacao.enteId().valor(),
                liquidacao.id().valor());
    }

    private Liquidacao mapear(TenantId enteId, ResultSet rs) throws SQLException {
        String aprovadorCpf = rs.getString("aprovador_cpf");
        return Liquidacao.reidratar(
                new LiquidacaoId(rs.getObject("id", UUID.class)),
                enteId,
                new EmpenhoId(rs.getObject("empenho_id", UUID.class)),
                rs.getDate("data_competencia").toLocalDate(),
                new Dinheiro(rs.getBigDecimal("valor")),
                lerDocumentos(rs.getString("documentos_suporte")),
                rs.getString("historico"),
                rs.getObject("fato_contabil_id", UUID.class),
                new Cpf(rs.getString("autor_cpf")),
                StatusAprovacao.valueOf(rs.getString("status_aprovacao").toUpperCase()),
                Optional.ofNullable(aprovadorCpf).map(Cpf::new),
                Optional.ofNullable(rs.getString("motivo_devolucao")));
    }

    private static String codigoStatus(StatusAprovacao status) {
        return status.name().toLowerCase();
    }

    private String escreverDocumentos(List<DocumentoSuporte> documentos) {
        try {
            return objectMapper.writeValueAsString(documentos.stream().map(DocumentoSuporteJson::de).toList());
        } catch (JsonProcessingException erro) {
            throw new ExecucaoInvalidaException(
                    "documento_suporte_invalido", "não foi possível serializar os documentos de suporte");
        }
    }

    private List<DocumentoSuporte> lerDocumentos(String json) {
        try {
            List<DocumentoSuporteJson> lidos = objectMapper.readValue(json, DOCUMENTOS_TYPE);
            return lidos.stream().map(DocumentoSuporteJson::paraDominio).toList();
        } catch (JsonProcessingException erro) {
            throw new ExecucaoInvalidaException(
                    "documento_suporte_invalido", "liquidação persistida com documentos de suporte inválidos");
        }
    }

    /**
     * Espelho serializável de {@link DocumentoSuporte}: o record de domínio usa {@code Optional}
     * (Jackson padrão não desserializa) e {@code LocalDate} (exigiria o módulo jsr310, que este
     * módulo não depende) — {@code dataEmissao} vira ISO-8601 (`yyyy-MM-dd`) em texto.
     */
    private record DocumentoSuporteJson(String tipo, String numero, String dataEmissao, String referenciaExterna) {

        static DocumentoSuporteJson de(DocumentoSuporte documento) {
            return new DocumentoSuporteJson(
                    documento.tipo(),
                    documento.numero(),
                    documento.dataEmissao().toString(),
                    documento.referenciaExterna().orElse(null));
        }

        DocumentoSuporte paraDominio() {
            return new DocumentoSuporte(tipo, numero, LocalDate.parse(dataEmissao), Optional.ofNullable(referenciaExterna));
        }
    }
}
