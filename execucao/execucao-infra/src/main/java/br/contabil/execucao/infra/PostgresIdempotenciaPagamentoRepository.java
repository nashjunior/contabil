package br.contabil.execucao.infra;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.contabil.execucao.domain.PagamentoId;
import br.contabil.execucao.domain.repository.IdempotenciaPagamentoRepository;
import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.TenantId;

/**
 * Adapter Postgres da idempotência de pagamento (ADR-0011/RAZ-134) — mesmo
 * padrão de {@code PostgresServicoEntrega} (outbox, ADR-0004): insert com
 * {@code on conflict (ente_id, chave) do nothing}; 0 linhas afetadas = a
 * chave já foi usada, devolve o {@link PagamentoId} já associado.
 */
@Repository
public class PostgresIdempotenciaPagamentoRepository implements IdempotenciaPagamentoRepository {

    private static final String SQL_INSERT =
            """
            insert into pagamento_idempotencia (id, ente_id, chave, pagamento_id)
            values (?, ?, ?, ?)
            on conflict (ente_id, chave) do nothing
            """;

    private static final String SQL_BUSCAR_PAGAMENTO_ID =
            "select pagamento_id from pagamento_idempotencia where ente_id = ? and chave = ?";

    private final JdbcTemplate jdbcTemplate;

    public PostgresIdempotenciaPagamentoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PagamentoId reservar(TenantId enteId, ChaveIdempotencia chave, PagamentoId candidato) {
        int linhasInseridas = jdbcTemplate.update(
                SQL_INSERT, UUID.randomUUID(), enteId.valor(), chave.valor(), candidato.valor());
        if (linhasInseridas == 0) {
            UUID idExistente = jdbcTemplate.queryForObject(
                    SQL_BUSCAR_PAGAMENTO_ID, UUID.class, enteId.valor(), chave.valor());
            return new PagamentoId(idExistente);
        }
        return candidato;
    }
}
