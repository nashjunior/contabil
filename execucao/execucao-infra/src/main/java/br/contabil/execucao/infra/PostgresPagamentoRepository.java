package br.contabil.execucao.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.contabil.execucao.domain.Beneficiario;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.NaturezaPagamento;
import br.contabil.execucao.domain.Pagamento;
import br.contabil.execucao.domain.PagamentoId;
import br.contabil.execucao.domain.repository.PagamentoRepository;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/** Adapter Postgres do pagamento (JdbcTemplate — mesma escolha do empenho/liquidação, sem JPA). */
@Repository
public class PostgresPagamentoRepository implements PagamentoRepository {

    private static final String SQL_INSERT =
            """
            insert into pagamento
                (id, ente_id, liquidacao_id, data_competencia, valor, natureza,
                 beneficiario_nome, beneficiario_cpf_cnpj, ordem_bancaria, historico, fato_contabil_id)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_BUSCAR =
            """
            select id, liquidacao_id, data_competencia, valor, natureza,
                   beneficiario_nome, beneficiario_cpf_cnpj, ordem_bancaria, historico, fato_contabil_id
              from pagamento
             where ente_id = ? and id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresPagamentoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void inserir(Pagamento pagamento) {
        jdbcTemplate.update(
                SQL_INSERT,
                pagamento.id().valor(),
                pagamento.enteId().valor(),
                pagamento.liquidacaoId().valor(),
                pagamento.dataCompetencia(),
                pagamento.valor().valor(),
                codigoNatureza(pagamento.natureza()),
                pagamento.beneficiario().map(Beneficiario::nome).orElse(null),
                pagamento.beneficiario().map(Beneficiario::cpfCnpj).orElse(null),
                pagamento.ordemBancaria().orElse(null),
                pagamento.historico(),
                pagamento.fatoContabilId());
    }

    @Override
    public Optional<Pagamento> buscarPorId(TenantId enteId, PagamentoId id) {
        List<Pagamento> linhas =
                jdbcTemplate.query(SQL_BUSCAR, (rs, rowNum) -> mapear(enteId, rs), enteId.valor(), id.valor());
        return linhas.stream().findFirst();
    }

    private static Pagamento mapear(TenantId enteId, ResultSet rs) throws SQLException {
        String nome = rs.getString("beneficiario_nome");
        String cpfCnpj = rs.getString("beneficiario_cpf_cnpj");
        Optional<Beneficiario> beneficiario =
                nome == null ? Optional.empty() : Optional.of(new Beneficiario(nome, cpfCnpj));

        return Pagamento.registrar(
                new PagamentoId(rs.getObject("id", UUID.class)),
                enteId,
                new LiquidacaoId(rs.getObject("liquidacao_id", UUID.class)),
                rs.getDate("data_competencia").toLocalDate(),
                new Dinheiro(rs.getBigDecimal("valor")),
                NaturezaPagamento.valueOf(rs.getString("natureza").toUpperCase()),
                beneficiario,
                Optional.ofNullable(rs.getString("ordem_bancaria")),
                rs.getString("historico"),
                rs.getObject("fato_contabil_id", UUID.class));
    }

    private static String codigoNatureza(NaturezaPagamento natureza) {
        return natureza.name().toLowerCase();
    }
}
