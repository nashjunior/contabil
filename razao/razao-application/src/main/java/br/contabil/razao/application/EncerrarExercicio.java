package br.contabil.razao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.EncerramentoConflitanteException;
import br.contabil.razao.domain.PeriodoContabil;
import br.contabil.razao.domain.PeriodoContabilNaoEncontradoException;
import br.contabil.razao.domain.StatusPeriodo;
import br.contabil.razao.domain.repository.PeriodoContabilRepository;

/**
 * Caso de uso: encerra o exercício contábil (mês 13) — RAZ-207/RAZ-226.
 *
 * <p>Fluxo: (1) RBAC+MFA (ADR-0046) antes de qualquer I/O; (2) carrega o período mês 13 e
 * verifica que está {@code ABERTO}; (3) inscreve os Restos a Pagar (RPNP e RPP) por fonte
 * via {@link InscreverRestosAPagar} — os fatos são gerados aqui, Σdébito=Σcrédito,
 * append-only, um por fonte com saldo positivo (docs/17-restos-a-pagar.md §Preciso); (4)
 * transiciona o período para {@code ENCERRADO} com guarda de concorrência (ADR-0045); (5)
 * registra evento de auditoria do encerramento.
 *
 * <p>Os códigos PCASP das contas de RP são injetados via {@link ParametroInscricaoRP} — o
 * use case é PCASP-agnóstico; {@code [REVALIDAR]} no MCASP edição vigente antes de
 * configurar em produção.
 */
public class EncerrarExercicio {

    private static final int MES_ENCERRAMENTO_EXERCICIO = 13;
    private static final Recurso RECURSO_PERIODO = new Recurso("razao:periodo_contabil");

    private final ControleAcesso controleAcesso;
    private final PeriodoContabilRepository periodoRepositorio;
    private final InscreverRestosAPagar inscreverRP;
    private final List<ParametroInscricaoRP> parametrosRP;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public EncerrarExercicio(
            ControleAcesso controleAcesso,
            PeriodoContabilRepository periodoRepositorio,
            InscreverRestosAPagar inscreverRP,
            List<ParametroInscricaoRP> parametrosRP,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.periodoRepositorio = Objects.requireNonNull(periodoRepositorio, "periodoRepositorio");
        this.inscreverRP = Objects.requireNonNull(inscreverRP, "inscreverRP");
        this.parametrosRP = List.copyOf(Objects.requireNonNull(parametrosRP, "parametrosRP"));
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.clock = Objects.requireNonNull(clock, "clock");
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

        // Inscrição de Restos a Pagar (RPNP e RPP) por fonte — RAZ-207.
        LocalDate dataEncerramento = LocalDate.of(exercicio, 12, 31);
        inscreverRP.executar(
                usuarioAutenticado, enteId, exercicio, periodo.id(), dataEncerramento, parametrosRP);

        PeriodoContabil encerrado = periodo.encerrarPeriodoExercicio(clock);

        boolean transicionou = periodoRepositorio.encerrar(encerrado);
        if (!transicionou) {
            throw new EncerramentoConflitanteException(periodo.id());
        }

        auditoria.append(new EventoAuditoria(
                enteId,
                "razao_exercicio_encerrado",
                usuarioAutenticado.titular().mascarado(),
                "razao:periodo_contabil:%s".formatted(encerrado.id().valor()),
                Instant.now(clock),
                Map.of("exercicio", String.valueOf(exercicio))));

        return encerrado;
    }
}
