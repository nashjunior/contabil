package br.contabil.execucao.application;

import java.util.Objects;
import java.util.Optional;

import br.contabil.execucao.domain.PaginaDotacoes;
import br.contabil.execucao.domain.repository.DotacoesQuery;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.consulta.ConsultaPaginada;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: lista dotações de um ente/exercício com saldo operacional inline
 * (RAZ-148/ADR-0038). A política de paginação fica na application; a borda HTTP
 * só adapta query params.
 */
public class ConsultarDotacoes {

    private static final Recurso RECURSO_DOTACAO = new Recurso("execucao:dotacao");

    private final ControleAcesso controleAcesso;
    private final DotacoesQuery dotacoes;

    public ConsultarDotacoes(ControleAcesso controleAcesso, DotacoesQuery dotacoes) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.dotacoes = Objects.requireNonNull(dotacoes, "dotacoes");
    }

    public PaginaDotacoes executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            int exercicio,
            Optional<String> busca,
            Optional<Integer> limite,
            Optional<String> cursor) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_DOTACAO, Acao.LER);
        return dotacoes.consultar(enteId, exercicio, busca.map(String::trim).filter(valor -> !valor.isBlank()),
                limiteEfetivo(limite), cursor);
    }

    private static int limiteEfetivo(Optional<Integer> limite) {
        int solicitado = limite.orElse(ConsultaPaginada.POR_PAGINA_PADRAO);
        if (solicitado < 1) {
            return ConsultaPaginada.POR_PAGINA_PADRAO;
        }
        return Math.min(solicitado, ConsultaPaginada.POR_PAGINA_MAXIMO);
    }
}
