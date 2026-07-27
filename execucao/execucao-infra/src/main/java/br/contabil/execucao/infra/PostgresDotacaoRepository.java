package br.contabil.execucao.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.contabil.execucao.domain.CreditoAdicional;
import br.contabil.execucao.domain.Dotacao;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.DotacaoRepository;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/** Adapter Postgres da dotação (JdbcTemplate — mesma escolha do empenho/razão, sem JPA). */
@Repository
public class PostgresDotacaoRepository implements DotacaoRepository {

    private static final String SQL_INSERT =
            """
            insert into dotacao
                (id, ente_id, exercicio, classificacao_orcamentaria, fonte_recurso,
                 unidade_gestora_id, valor_autorizado)
            values (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_BUSCAR =
            """
            select id, exercicio, classificacao_orcamentaria, fonte_recurso, unidade_gestora_id, valor_autorizado
              from dotacao
             where ente_id = ? and id = ?
            """;

    // ON CONFLICT DO NOTHING: reentrega idempotente (ADR-0011) do mesmo id não falha o lote —
    // aparece com contagem de linha 0 e o caller (inserirEmLote) reporta em erros.
    private static final String SQL_INSERT_LOTE =
            """
            insert into dotacao
                (id, ente_id, exercicio, classificacao_orcamentaria, fonte_recurso,
                 unidade_gestora_id, valor_autorizado)
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do nothing
            """;

    // Soma atômica em valor_autorizado (Lei 4.320/1964 arts. 40-46): um único UPDATE, sem
    // select prévio — o próprio UPDATE toma a trava de linha, não há janela de corrida entre
    // ler e escrever (diferente do saldoDotacao, que precisa ler para decidir em Java antes de
    // escrever em outra tabela). Contagem de linha 0 = dotação inexistente para o ente.
    private static final String SQL_APLICAR_CREDITO_LOTE =
            """
            update dotacao
               set valor_autorizado = valor_autorizado + ?
             where ente_id = ? and id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresDotacaoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void inserir(Dotacao dotacao) {
        jdbcTemplate.update(
                SQL_INSERT,
                dotacao.id().valor(),
                dotacao.enteId().valor(),
                dotacao.exercicio(),
                dotacao.classificacaoOrcamentaria(),
                dotacao.fonteRecurso(),
                dotacao.unidadeGestoraId().valor(),
                dotacao.valorAutorizado().valor());
    }

    @Override
    public Optional<Dotacao> buscarPorId(TenantId enteId, DotacaoId id) {
        List<Dotacao> linhas =
                jdbcTemplate.query(SQL_BUSCAR, (rs, rowNum) -> mapear(enteId, rs), enteId.valor(), id.valor());
        return linhas.stream().findFirst();
    }

    @Override
    public ResultadoLote<DotacaoId> inserirEmLote(List<Dotacao> dotacoes) {
        if (dotacoes.isEmpty()) {
            return new ResultadoLote<>(List.of(), List.of());
        }
        List<Object[]> argumentos = new ArrayList<>(dotacoes.size());
        for (Dotacao dotacao : dotacoes) {
            argumentos.add(new Object[] {
                dotacao.id().valor(),
                dotacao.enteId().valor(),
                dotacao.exercicio(),
                dotacao.classificacaoOrcamentaria(),
                dotacao.fonteRecurso(),
                dotacao.unidadeGestoraId().valor(),
                dotacao.valorAutorizado().valor()
            });
        }
        int[] linhasAfetadas = jdbcTemplate.batchUpdate(SQL_INSERT_LOTE, argumentos);

        List<DotacaoId> inseridas = new ArrayList<>();
        List<ErroItemLote> erros = new ArrayList<>();
        for (int i = 0; i < dotacoes.size(); i++) {
            DotacaoId id = dotacoes.get(i).id();
            if (linhasAfetadas[i] == 0) {
                erros.add(new ErroItemLote(
                        id.valor().toString(),
                        "dotacao_duplicada",
                        "dotação já existe (id duplicado) — ignorada (reentrega idempotente)"));
            } else {
                inseridas.add(id);
            }
        }
        return new ResultadoLote<>(inseridas, erros);
    }

    @Override
    public ResultadoLote<DotacaoId> aplicarCreditosEmLote(TenantId enteId, List<CreditoAdicional> creditos) {
        if (creditos.isEmpty()) {
            return new ResultadoLote<>(List.of(), List.of());
        }
        List<Object[]> argumentos = new ArrayList<>(creditos.size());
        for (CreditoAdicional credito : creditos) {
            argumentos.add(new Object[] {credito.valor().valor(), enteId.valor(), credito.dotacaoId().valor()});
        }
        int[] linhasAfetadas = jdbcTemplate.batchUpdate(SQL_APLICAR_CREDITO_LOTE, argumentos);

        List<DotacaoId> atualizadas = new ArrayList<>();
        List<ErroItemLote> erros = new ArrayList<>();
        for (int i = 0; i < creditos.size(); i++) {
            DotacaoId id = creditos.get(i).dotacaoId();
            if (linhasAfetadas[i] == 0) {
                erros.add(new ErroItemLote(
                        id.valor().toString(),
                        "dotacao_nao_encontrada",
                        "dotação não encontrada para o ente — crédito não aplicado"));
            } else {
                atualizadas.add(id);
            }
        }
        return new ResultadoLote<>(atualizadas, erros);
    }

    private static Dotacao mapear(TenantId enteId, ResultSet rs) throws SQLException {
        return Dotacao.registrar(
                new DotacaoId(rs.getObject("id", UUID.class)),
                enteId,
                rs.getInt("exercicio"),
                rs.getString("classificacao_orcamentaria"),
                rs.getString("fonte_recurso"),
                new UnidadeGestoraId(rs.getObject("unidade_gestora_id", UUID.class)),
                new Dinheiro(rs.getBigDecimal("valor_autorizado")));
    }
}
