package br.contabil.razao.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.PeriodoContabil;
import br.contabil.razao.domain.PeriodoContabilNaoEncontradoException;
import br.contabil.razao.domain.PeriodoJaEncerradoException;
import br.contabil.razao.domain.repository.PeriodoContabilRepository;

/**
 * Caso de uso: encerra um período contábil comum (mês 1–12) — RAZ-206,
 * docs/15-fechamento-contabil.md §Preciso item 1. {@link ControleAcesso}
 * exige {@link Acao#ENCERRAR} (RBAC + MFA, ADR-0046 — papel dedicado, sem
 * checagem de autor individual). A
 * transição {@code aberto -> encerrado} é condicional no repositório
 * (ADR-0045); mês 13 é recusado por {@link PeriodoContabil#encerrar} —
 * encerramento de exercício exige apuração de resultado, escopo de
 * {@code EncerrarExercicio} (ainda não implementado).
 *
 * <p><b>Fora deste incremento:</b> a validação de "ausência de pendência que
 * impeça" (docs/15 §Preciso item 1) não está implementada — nenhuma fonte
 * pesquisada definiu a regra concreta de pendência para o encerramento de
 * período; ver issue filha de {@code EncerrarExercicio}/DCASP.
 */
public class EncerrarPeriodo {

    private static final Recurso RECURSO_PERIODO = new Recurso("razao:periodo_contabil");

    private final ControleAcesso controleAcesso;
    private final PeriodoContabilRepository periodoRepositorio;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public EncerrarPeriodo(
            ControleAcesso controleAcesso,
            PeriodoContabilRepository periodoRepositorio,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.controleAcesso = controleAcesso;
        this.periodoRepositorio = periodoRepositorio;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public PeriodoContabil executar(Sessao usuarioAutenticado, TenantId enteId, int exercicio, int mes) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_PERIODO, Acao.ENCERRAR);

        PeriodoContabil periodo = periodoRepositorio
                .buscarPorCompetencia(enteId, exercicio, mes)
                .orElseThrow(() -> new PeriodoContabilNaoEncontradoException(enteId, exercicio, mes));

        PeriodoContabil encerrado = periodo.encerrar(clock);

        boolean transicionou = periodoRepositorio.encerrar(encerrado);
        if (!transicionou) {
            // Corrida real: o snapshot lido estava ABERTO, mas outra chamada concorrente encerrou
            // antes deste UPDATE (mesma mecânica do AprovarPagamento/ADR-0023) — nunca sobrescreve.
            throw new PeriodoJaEncerradoException(periodo.id());
        }

        auditoria.append(new EventoAuditoria(
                enteId,
                "razao_periodo_contabil_encerrado",
                usuarioAutenticado.titular().mascarado(),
                "razao:periodo_contabil:%s".formatted(encerrado.id().valor()),
                Instant.now(clock),
                Map.of("exercicio", String.valueOf(exercicio), "mes", String.valueOf(mes))));

        return encerrado;
    }
}
