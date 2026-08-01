package br.contabil.razao.application;

import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.EncerramentoConflitanteException;
import br.contabil.razao.domain.EncerramentoExercicioPendenteRestosAPagarException;
import br.contabil.razao.domain.PeriodoContabil;
import br.contabil.razao.domain.PeriodoContabilNaoEncontradoException;
import br.contabil.razao.domain.StatusPeriodo;
import br.contabil.razao.domain.repository.PeriodoContabilRepository;

/**
 * Caso de uso: encerra o exercício contábil (mês 13) — RAZ-226.
 *
 * <p>O boundary já aplica as guardas estáveis de ADR-0045/ADR-0046: RBAC+MFA,
 * competência fixa no mês 13 e estado aberto do período. O roteiro MCASP/IPC-03
 * do encerramento patrimonial/orçamentário já está confirmado em docs/15 (RAZ-232);
 * a geração dos fatos segue bloqueada até a RAZ-207 expor o contrato de inscrição
 * de Restos a Pagar/trava art. 42. Enquanto isso, este use case falha antes de
 * qualquer escrita no razão ou trilha de encerramento.
 */
public class EncerrarExercicio {

    private static final int MES_ENCERRAMENTO_EXERCICIO = 13;
    private static final Recurso RECURSO_PERIODO = new Recurso("razao:periodo_contabil");

    private final ControleAcesso controleAcesso;
    private final PeriodoContabilRepository periodoRepositorio;

    public EncerrarExercicio(
            ControleAcesso controleAcesso,
            PeriodoContabilRepository periodoRepositorio) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.periodoRepositorio = Objects.requireNonNull(periodoRepositorio, "periodoRepositorio");
    }

    public PeriodoContabil executar(Sessao usuarioAutenticado, TenantId enteId, int exercicio) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_PERIODO, Acao.ENCERRAR);

        PeriodoContabil periodo = periodoRepositorio
                .buscarPorCompetencia(enteId, exercicio, MES_ENCERRAMENTO_EXERCICIO)
                .orElseThrow(() -> new PeriodoContabilNaoEncontradoException(
                        enteId, exercicio, MES_ENCERRAMENTO_EXERCICIO));

        if (periodo.status() != StatusPeriodo.ABERTO) {
            throw new EncerramentoConflitanteException(periodo.id());
        }

        throw new EncerramentoExercicioPendenteRestosAPagarException(exercicio);
    }
}
