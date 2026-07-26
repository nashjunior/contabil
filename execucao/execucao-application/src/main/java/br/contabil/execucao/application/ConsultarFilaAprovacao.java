package br.contabil.execucao.application;

import java.util.Objects;
import java.util.Optional;

import br.contabil.execucao.domain.FiltroFilaAprovacao;
import br.contabil.execucao.domain.PaginaFilaAprovacao;
import br.contabil.execucao.domain.repository.FilaAprovacaoQuery;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.consulta.ConsultaPaginada;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: fila de liquidações por {@code statusAprovacao} para o gate 4-eyes
 * (ADR-0029 §1/§2). {@link Acao#LER} sobre {@code execucao:liquidacao} (nunca exige
 * MFA — RAZ-33); {@code executar(..)} cai no advisor de tenant/RLS de
 * {@code TenantContextUseCasesConfiguration}, então a query roda com {@code
 * app.ente_id} setado (isolamento por ente, ADR-0016).
 *
 * <p>A segregação da Regra 9 é imposta na query a partir de {@code sessao.titular()}
 * — o autor nunca vê o próprio item pendente, nem pode aprová-lo (defense-in-depth).
 * O {@code limite} é clampado aqui (default 20, máx. 100 — mesmos limites de
 * {@link ConsultaPaginada}); a borda HTTP não decide política de paginação.
 */
public class ConsultarFilaAprovacao {

    private static final Recurso RECURSO_LIQUIDACAO = new Recurso("execucao:liquidacao");

    private final ControleAcesso controleAcesso;
    private final FilaAprovacaoQuery filaAprovacao;

    public ConsultarFilaAprovacao(ControleAcesso controleAcesso, FilaAprovacaoQuery filaAprovacao) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.filaAprovacao = Objects.requireNonNull(filaAprovacao, "filaAprovacao");
    }

    public PaginaFilaAprovacao executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            FiltroFilaAprovacao filtro,
            Optional<Integer> limite,
            Optional<String> cursor) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_LIQUIDACAO, Acao.LER);
        return filaAprovacao.consultar(
                enteId, usuarioAutenticado.titular(), filtro, limiteEfetivo(limite), cursor);
    }

    private static int limiteEfetivo(Optional<Integer> limite) {
        int solicitado = limite.orElse(ConsultaPaginada.POR_PAGINA_PADRAO);
        if (solicitado < 1) {
            return ConsultaPaginada.POR_PAGINA_PADRAO;
        }
        return Math.min(solicitado, ConsultaPaginada.POR_PAGINA_MAXIMO);
    }
}
