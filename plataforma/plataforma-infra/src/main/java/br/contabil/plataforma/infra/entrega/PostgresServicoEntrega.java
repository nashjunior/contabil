package br.contabil.plataforma.infra.entrega;

import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.entrega.ServicoEntrega;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter Postgres do outbox de entrega garantida (ADR-0004/0011). {@code enqueue}
 * grava a intenção de publicar na MESMA transação do fato que a originou — o
 * despacho assíncrono ao destino real fica no worker/broker de entrega.
 *
 * <p>Fecha o gap de wiring do RAZ-9: sem este bean, qualquer caso de uso que
 * dependa de {@link ServicoEntrega} (ex.: publicação na transparência) impede
 * o contexto Spring inteiro de subir.
 */
@Component
public class PostgresServicoEntrega implements ServicoEntrega {

    private static final String SQL_INSERT =
            """
            insert into outbox_mensagem (id, ente_id, chave, destino, tipo, conteudo)
            values (?, ?, ?, ?, ?, ?)
            on conflict (ente_id, chave) do nothing
            """;

    private static final String SQL_BUSCAR_ID_POR_CHAVE =
            "select id from outbox_mensagem where ente_id = ? and chave = ?";

    private static final String SQL_STATUS = "select status from outbox_mensagem where id = ?";

    private final JdbcTemplate jdbcTemplate;

    public PostgresServicoEntrega(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public IdEntrega enqueue(MensagemEntrega mensagem, ChaveIdempotencia chave) {
        UUID id = UUID.randomUUID();
        int linhasInseridas = jdbcTemplate.update(
                SQL_INSERT,
                id,
                mensagem.ente().valor(),
                chave.valor(),
                mensagem.destino(),
                mensagem.tipo(),
                mensagem.conteudo());
        if (linhasInseridas == 0) {
            UUID idExistente = jdbcTemplate.queryForObject(
                    SQL_BUSCAR_ID_POR_CHAVE, UUID.class, mensagem.ente().valor(), chave.valor());
            return new IdEntrega(idExistente);
        }
        return new IdEntrega(id);
    }

    @Override
    public StatusEntrega status(IdEntrega id) {
        List<String> linhas = jdbcTemplate.query(
                SQL_STATUS, (rs, rowNum) -> rs.getString("status"), id.valor());
        if (linhas.isEmpty()) {
            throw new IllegalArgumentException("entrega desconhecida: " + id);
        }
        return StatusEntrega.valueOf(linhas.get(0).toUpperCase(Locale.ROOT));
    }
}
