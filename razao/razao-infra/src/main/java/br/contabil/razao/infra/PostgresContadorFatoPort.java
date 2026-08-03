package br.contabil.razao.infra;

import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.Validacoes;
import br.contabil.razao.domain.repository.ContadorFatoPort;

/**
 * Numeração sequencial cronológica gapless — delega à função
 * {@code proximo_numero_seq()} (razao-contabil-schema.md), que faz o
 * {@code UPDATE ... RETURNING} com lock de linha em {@code contador_fato} na
 * mesma transação do use case chamador ({@code @Transactional}).
 *
 * <p>A função SQL NÃO recebe {@code enteId} como argumento — ela deriva o
 * ente de {@code current_setting('app.ente_id')} no banco, a mesma variável
 * de sessão usada pela RLS (RAZ-14): passar o ente como parâmetro de SQL
 * permitiria a um chamador malicioso incrementar a sequência de outro ente,
 * já que a função é {@code security definer} e ignora RLS. {@code enteId}
 * aqui só garante fail-fast contra chamada com tenant nulo; quem efetivamente
 * amarra o número gerado ao ente correto é o {@code app.ente_id} já setado na
 * conexão/transação, não este parâmetro.
 */
@Component
public class PostgresContadorFatoPort implements ContadorFatoPort {

    private static final String SQL_PROXIMO_NUMERO_SEQ = "select proximo_numero_seq()";

    private final JdbcTemplate jdbcTemplate;

    public PostgresContadorFatoPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long proximoNumeroSeq(TenantId enteId) {
        Validacoes.exigirNaoNulo(enteId, "enteId");
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(SQL_PROXIMO_NUMERO_SEQ, Long.class),
                "proximo_numero_seq retornou null");
    }
}
