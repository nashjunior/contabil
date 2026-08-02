package br.contabil.execucao.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.contabil.execucao.domain.JanelaMandato;
import br.contabil.execucao.domain.repository.JanelaMandatoPort;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.Validacoes;

/**
 * Lê a janela de mandato vigente das colunas {@code mandato_inicio}/{@code
 * mandato_fim} de {@code ente} (RAZ-243/ADR-0044) — config do ente, não hard-coded.
 * {@code ente} não tem {@code ente_id} próprio (é a raiz multi-tenant) — filtra por
 * {@code id}, fora do escopo de RLS (que protege as tabelas dependentes).
 */
@Component
public class PostgresJanelaMandatoPort implements JanelaMandatoPort {

    private static final String SQL_MANDATO =
            "select mandato_inicio, mandato_fim from ente where id = ?";

    private final JdbcTemplate jdbcTemplate;

    public PostgresJanelaMandatoPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<JanelaMandato> buscar(TenantId enteId) {
        Validacoes.exigirNaoNulo(enteId, "enteId");
        List<JanelaMandato> linhas = jdbcTemplate.query(
                SQL_MANDATO,
                (rs, rowNum) -> {
                    LocalDate inicio = rs.getObject("mandato_inicio", LocalDate.class);
                    LocalDate fim = rs.getObject("mandato_fim", LocalDate.class);
                    return inicio == null || fim == null ? null : new JanelaMandato(inicio, fim);
                },
                enteId.valor());
        return linhas.isEmpty() ? Optional.empty() : Optional.ofNullable(linhas.get(0));
    }
}
