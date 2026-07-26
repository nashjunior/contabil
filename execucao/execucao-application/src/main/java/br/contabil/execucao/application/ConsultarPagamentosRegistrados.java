package br.contabil.execucao.application;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import br.contabil.execucao.domain.PaginaPagamentosRegistrados;
import br.contabil.execucao.domain.repository.PagamentosRegistradosQuery;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.consulta.ConsultaPaginada;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: lista/pagina os pagamentos registrados de um ente por período
 * (RAZ-121) — mesmo padrão de {@link ConsultarEmpenhosRegistrados}/{@link
 * ConsultarLiquidacoesRegistradas}, só troca o port.
 */
public class ConsultarPagamentosRegistrados {

    private static final Recurso RECURSO_PAGAMENTO = new Recurso("execucao:pagamento");

    private final ControleAcesso controleAcesso;
    private final PagamentosRegistradosQuery pagamentosRegistrados;

    public ConsultarPagamentosRegistrados(
            ControleAcesso controleAcesso, PagamentosRegistradosQuery pagamentosRegistrados) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.pagamentosRegistrados = Objects.requireNonNull(pagamentosRegistrados, "pagamentosRegistrados");
    }

    public PaginaPagamentosRegistrados executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            Optional<LocalDate> dataInicio,
            Optional<LocalDate> dataFim,
            Optional<Integer> limite,
            Optional<String> cursor) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_PAGAMENTO, Acao.LER);
        return pagamentosRegistrados.consultar(enteId, dataInicio, dataFim, limiteEfetivo(limite), cursor);
    }

    private static int limiteEfetivo(Optional<Integer> limite) {
        int solicitado = limite.orElse(ConsultaPaginada.POR_PAGINA_PADRAO);
        if (solicitado < 1) {
            return ConsultaPaginada.POR_PAGINA_PADRAO;
        }
        return Math.min(solicitado, ConsultaPaginada.POR_PAGINA_MAXIMO);
    }
}
