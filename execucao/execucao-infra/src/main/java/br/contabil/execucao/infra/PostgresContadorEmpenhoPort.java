package br.contabil.execucao.infra;

import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.contabil.execucao.domain.repository.ContadorEmpenhoPort;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.Validacoes;

/**
 * Numeração sequencial gapless do empenho por ente/exercício — delega à função
 * {@code proximo_numero_empenho(exercicio)}, que faz {@code UPDATE ... RETURNING}
 * com lock de linha em {@code contador_empenho} na mesma transação do use case
 * chamador. A função deriva o ente de {@code current_setting('app.ente_id')}
 * (mesmo padrão do {@code proximo_numero_seq()} do razão — RAZ-14): nunca de um
 * parâmetro de chamada.
 */
@Component
public class PostgresContadorEmpenhoPort implements ContadorEmpenhoPort {

    private static final String SQL_PROXIMO_NUMERO = "select proximo_numero_empenho(?)";

    private final JdbcTemplate jdbcTemplate;

    public PostgresContadorEmpenhoPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long proximoNumero(TenantId enteId, int exercicio) {
        Validacoes.exigirNaoNulo(enteId, "enteId");
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(SQL_PROXIMO_NUMERO, Long.class, exercicio),
                "proximo_numero_empenho retornou null");
    }
}
