package br.contabil.execucao.application;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import br.contabil.execucao.domain.PaginaEmpenhosRegistrados;
import br.contabil.execucao.domain.repository.EmpenhosRegistradosQuery;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.consulta.ConsultaPaginada;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: lista/pagina os empenhos registrados de um ente, opcionalmente
 * recortado por exercício/período (RAZ-121 — gap encontrado no RAZ-120: a tela
 * mínima só tinha o cache de sessão do React Query alimentado pelas respostas
 * dos POSTs, não uma consulta persistida). {@link Acao#LER} sobre {@code
 * execucao:empenho} (nunca exige MFA — RAZ-33); {@code executar(..)} cai no
 * advisor de tenant/RLS de {@code TenantContextUseCasesConfiguration}, mesmo
 * padrão de {@link ConsultarFilaAprovacao} (e de {@code razao.ConsultarContas}).
 * O {@code limite} é clampado aqui (default 20, máx. 100 — mesmos limites de
 * {@link ConsultaPaginada}); a borda HTTP não decide política de paginação.
 */
public class ConsultarEmpenhosRegistrados {

    private static final Recurso RECURSO_EMPENHO = new Recurso("execucao:empenho");

    private final ControleAcesso controleAcesso;
    private final EmpenhosRegistradosQuery empenhosRegistrados;

    public ConsultarEmpenhosRegistrados(ControleAcesso controleAcesso, EmpenhosRegistradosQuery empenhosRegistrados) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.empenhosRegistrados = Objects.requireNonNull(empenhosRegistrados, "empenhosRegistrados");
    }

    public PaginaEmpenhosRegistrados executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            Optional<Integer> exercicio,
            Optional<LocalDate> dataInicio,
            Optional<LocalDate> dataFim,
            Optional<Integer> limite,
            Optional<String> cursor) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_EMPENHO, Acao.LER);
        return empenhosRegistrados.consultar(enteId, exercicio, dataInicio, dataFim, limiteEfetivo(limite), cursor);
    }

    private static int limiteEfetivo(Optional<Integer> limite) {
        int solicitado = limite.orElse(ConsultaPaginada.POR_PAGINA_PADRAO);
        if (solicitado < 1) {
            return ConsultaPaginada.POR_PAGINA_PADRAO;
        }
        return Math.min(solicitado, ConsultaPaginada.POR_PAGINA_MAXIMO);
    }
}
