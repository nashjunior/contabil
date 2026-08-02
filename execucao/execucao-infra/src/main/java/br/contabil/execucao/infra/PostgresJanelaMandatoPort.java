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
 *
 * <p>{@code ente} não tem grant nenhum para {@code app_role} (RAZ-17: um {@code
 * select} direto vazaria o catálogo inteiro de entes, já que {@code ente} é a raiz
 * multi-tenant sem {@code ente_id} próprio para a RLS filtrar) — a leitura passa
 * pela função {@code security definer} {@code mandato_vigente_do_ente()} (RAZ-263,
 * V16, mesmo padrão de {@code proximo_numero_seq()} em V1). A função deriva o ente
 * de {@code current_setting('app.ente_id')} (setado por {@code
 * TenantContextUseCasesConfiguration} a partir do MESMO {@link TenantId} recebido
 * aqui), nunca de um parâmetro — por isso {@code enteId} não vira bind param do
 * SQL: passá-lo à função abriria a mesma brecha cross-tenant que
 * {@code proximo_numero_seq()} evita ao não aceitar {@code p_ente_id}. O
 * argumento continua exigido/validado para manter o contrato de {@link
 * JanelaMandatoPort} explícito sobre qual ente se pergunta, mesmo a query
 * ignorando-o em favor da sessão RLS (que é sempre o mesmo tenant, por construção
 * do advisor).
 */
@Component
public class PostgresJanelaMandatoPort implements JanelaMandatoPort {

    private static final String SQL_MANDATO = "select mandato_inicio, mandato_fim from mandato_vigente_do_ente()";

    private final JdbcTemplate jdbcTemplate;

    public PostgresJanelaMandatoPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<JanelaMandato> buscar(TenantId enteId) {
        Validacoes.exigirNaoNulo(enteId, "enteId");
        List<JanelaMandato> linhas = jdbcTemplate.query(SQL_MANDATO, (rs, rowNum) -> {
            LocalDate inicio = rs.getObject("mandato_inicio", LocalDate.class);
            LocalDate fim = rs.getObject("mandato_fim", LocalDate.class);
            return inicio == null || fim == null ? null : new JanelaMandato(inicio, fim);
        });
        return linhas.isEmpty() ? Optional.empty() : Optional.ofNullable(linhas.get(0));
    }
}
