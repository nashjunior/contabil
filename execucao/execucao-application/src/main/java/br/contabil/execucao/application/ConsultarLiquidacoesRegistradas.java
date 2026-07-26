package br.contabil.execucao.application;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import br.contabil.execucao.domain.PaginaLiquidacoesRegistradas;
import br.contabil.execucao.domain.repository.LiquidacoesRegistradasQuery;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.consulta.ConsultaPaginada;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: lista/pagina as liquidações registradas de um ente por período
 * (RAZ-121). Distinto de {@link ConsultarFilaAprovacao}: aquele serve o gate
 * de aprovação (recorte por {@code statusAprovacao}, Regra 9 imposta na query);
 * este é o registro completo — mesmo padrão de {@link
 * ConsultarEmpenhosRegistrados}, só troca o port.
 */
public class ConsultarLiquidacoesRegistradas {

    private static final Recurso RECURSO_LIQUIDACAO = new Recurso("execucao:liquidacao");

    private final ControleAcesso controleAcesso;
    private final LiquidacoesRegistradasQuery liquidacoesRegistradas;

    public ConsultarLiquidacoesRegistradas(
            ControleAcesso controleAcesso, LiquidacoesRegistradasQuery liquidacoesRegistradas) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.liquidacoesRegistradas = Objects.requireNonNull(liquidacoesRegistradas, "liquidacoesRegistradas");
    }

    public PaginaLiquidacoesRegistradas executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            Optional<LocalDate> dataInicio,
            Optional<LocalDate> dataFim,
            Optional<Integer> limite,
            Optional<String> cursor) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_LIQUIDACAO, Acao.LER);
        return liquidacoesRegistradas.consultar(enteId, dataInicio, dataFim, limiteEfetivo(limite), cursor);
    }

    private static int limiteEfetivo(Optional<Integer> limite) {
        int solicitado = limite.orElse(ConsultaPaginada.POR_PAGINA_PADRAO);
        if (solicitado < 1) {
            return ConsultaPaginada.POR_PAGINA_PADRAO;
        }
        return Math.min(solicitado, ConsultaPaginada.POR_PAGINA_MAXIMO);
    }
}
