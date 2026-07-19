package br.contabil.razao.infra;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.application.ContadorFatoPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Numeração sequencial cronológica gapless — delega à função
 * {@code proximo_numero_seq(uuid)} (razao-contabil-schema.md), que faz o
 * {@code UPDATE ... RETURNING} com lock de linha em {@code contador_fato} na
 * mesma transação do use case chamador ({@code @Transactional}).
 */
@Component
public class PostgresContadorFatoPort implements ContadorFatoPort {

    private static final String SQL_PROXIMO_NUMERO_SEQ = "select proximo_numero_seq(?)";

    private final JdbcTemplate jdbcTemplate;

    public PostgresContadorFatoPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long proximoNumeroSeq(TenantId enteId) {
        Long numero = jdbcTemplate.queryForObject(SQL_PROXIMO_NUMERO_SEQ, Long.class, enteId.valor());
        return numero;
    }
}
