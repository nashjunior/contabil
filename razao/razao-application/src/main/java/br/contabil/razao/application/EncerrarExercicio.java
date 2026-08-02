package br.contabil.razao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.EncerramentoConflitanteException;
import br.contabil.razao.domain.PeriodoContabil;
import br.contabil.razao.domain.PeriodoContabilNaoEncontradoException;
import br.contabil.razao.domain.StatusPeriodo;
import br.contabil.razao.domain.repository.PeriodoContabilRepository;

/**
 * Caso de uso: encerra o exercício contábil (mês 13) e abre o seguinte —
 * RAZ-207/RAZ-226/RAZ-257/RAZ-258/RAZ-244/RAZ-259.
 *
 * <p>Fluxo: (1) RBAC+MFA (ADR-0046) antes de qualquer I/O; (2) carrega o período mês 13 e
 * verifica que está {@code ABERTO}; (3) apura o resultado patrimonial (VPA/VPD classes 3/4
 * contra a conta de PL) via {@link ApurarResultadoPatrimonial} (RAZ-257, docs/15 §Preciso
 * item 2, IPC 03/STN itens 19-34) — contas independentes das demais abaixo, sem ordem
 * obrigatória entre si; (4) inscreve os Restos a Pagar (RPNP e RPP) por fonte via
 * {@link InscreverRestosAPagar} — os fatos são gerados aqui, Σdébito=Σcrédito, append-only,
 * um por fonte com saldo positivo (docs/17-restos-a-pagar.md §Preciso); (5) encerra as
 * contas de controle orçamentário PCASP classes 5/6 via {@link EncerrarContasOrcamentarias}
 * — <b>depois</b> da inscrição de RP, para que os saldos já transferidos às contas de RP
 * não sejam também zerados aqui (docs/15 §Preciso item 3: RP do exercício corrente vira
 * lastro do próximo exercício, não é encerrado); (6) encerra a DDR **utilizada** por fonte
 * via {@link EncerrarDdrPorFonte} (RAZ-244, docs/15 §Preciso item 4, IPC 03/STN §91) — as
 * comprometidas/liquidadas não encerram, transitam como lastro dos RP; (7) transiciona o
 * período para {@code ENCERRADO} com guarda de concorrência (ADR-0045); (8) abre o
 * exercício seguinte via {@link TransporSaldosAbertura} (RAZ-259, docs/15 §Preciso item 5,
 * IPC 03/STN item 30) — transposição append-only de saldos patrimoniais permanentes em
 * 1º/jan, incluindo {@code 2.3.7.1.1.01.00 → 2.3.7.1.1.02.00}; (9) transpõe o superávit
 * financeiro da DDR por fonte via {@link TransporDdrPorFonteAbertura} (RAZ-244, docs/15
 * §Preciso item 5, IPC 03/STN §96) — base do gate art. 42 no exercício seguinte; (10)
 * registra evento de auditoria do encerramento. Todo o rito é all-or-nothing: qualquer
 * falha em (3)-(9) propaga e a transação da borda desfaz os fatos já gerados nesta chamada
 * (nenhum é commitado isoladamente) — inclusive se o período mês 1 do exercício seguinte
 * ainda não existir (a abertura propaga {@link br.contabil.razao.domain.PeriodoEncerradoException}).
 *
 * <p>Os códigos PCASP das contas de RP, de encerramento orçamentário e da conta de resultado
 * apurado são injetados via {@link ParametroInscricaoRP}/{@link ParametroEncerramentoOrcamentario}/
 * {@link ParametroTransposicaoAbertura}/{@code contaResultadoApurado} — o use case é
 * PCASP-agnóstico; {@code [REVALIDAR]} no MCASP edição vigente antes de configurar em
 * produção. Os parâmetros da DDR ({@link ParametroEncerramentoDdr}/
 * {@link ParametroTransposicaoDdrAbertura}) são resolvidos <b>por ente</b> em tempo de
 * execução via {@link ParametrosEncerramentoDdr}/{@link ParametrosTransposicaoDdrAbertura}
 * (RAZ-266, mesmo padrão do RAZ-260 para RP) — {@code conta_pcasp} é tenant-scoped.
 */
public class EncerrarExercicio {

    private static final int MES_ENCERRAMENTO_EXERCICIO = 13;
    private static final Recurso RECURSO_PERIODO = new Recurso("razao:periodo_contabil");

    private final ControleAcesso controleAcesso;
    private final PeriodoContabilRepository periodoRepositorio;
    private final ApurarResultadoPatrimonial apurarResultadoPatrimonial;
    private final Optional<ContaContabilId> contaResultadoApurado;
    private final InscreverRestosAPagar inscreverRP;
    private final List<ParametroInscricaoRP> parametrosRP;
    private final EncerrarContasOrcamentarias encerrarContasOrcamentarias;
    private final List<ParametroEncerramentoOrcamentario> parametrosEncerramentoOrcamentario;
    private final EncerrarDdrPorFonte encerrarDdr;
    private final ParametrosEncerramentoDdr parametrosDdr;
    private final TransporSaldosAbertura transporSaldosAbertura;
    private final List<ParametroTransposicaoAbertura> parametrosAbertura;
    private final TransporDdrPorFonteAbertura transporDdrAbertura;
    private final ParametrosTransposicaoDdrAbertura parametrosAberturaDdr;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public EncerrarExercicio(
            ControleAcesso controleAcesso,
            PeriodoContabilRepository periodoRepositorio,
            ApurarResultadoPatrimonial apurarResultadoPatrimonial,
            Optional<ContaContabilId> contaResultadoApurado,
            InscreverRestosAPagar inscreverRP,
            List<ParametroInscricaoRP> parametrosRP,
            EncerrarContasOrcamentarias encerrarContasOrcamentarias,
            List<ParametroEncerramentoOrcamentario> parametrosEncerramentoOrcamentario,
            EncerrarDdrPorFonte encerrarDdr,
            ParametrosEncerramentoDdr parametrosDdr,
            TransporSaldosAbertura transporSaldosAbertura,
            List<ParametroTransposicaoAbertura> parametrosAbertura,
            TransporDdrPorFonteAbertura transporDdrAbertura,
            ParametrosTransposicaoDdrAbertura parametrosAberturaDdr,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.periodoRepositorio = Objects.requireNonNull(periodoRepositorio, "periodoRepositorio");
        this.apurarResultadoPatrimonial =
                Objects.requireNonNull(apurarResultadoPatrimonial, "apurarResultadoPatrimonial");
        this.contaResultadoApurado = Objects.requireNonNull(contaResultadoApurado, "contaResultadoApurado");
        this.inscreverRP = Objects.requireNonNull(inscreverRP, "inscreverRP");
        this.parametrosRP = List.copyOf(Objects.requireNonNull(parametrosRP, "parametrosRP"));
        this.encerrarContasOrcamentarias =
                Objects.requireNonNull(encerrarContasOrcamentarias, "encerrarContasOrcamentarias");
        this.parametrosEncerramentoOrcamentario = List.copyOf(
                Objects.requireNonNull(parametrosEncerramentoOrcamentario, "parametrosEncerramentoOrcamentario"));
        this.encerrarDdr = Objects.requireNonNull(encerrarDdr, "encerrarDdr");
        this.parametrosDdr = Objects.requireNonNull(parametrosDdr, "parametrosDdr");
        this.transporSaldosAbertura = Objects.requireNonNull(transporSaldosAbertura, "transporSaldosAbertura");
        this.parametrosAbertura = List.copyOf(Objects.requireNonNull(parametrosAbertura, "parametrosAbertura"));
        this.transporDdrAbertura = Objects.requireNonNull(transporDdrAbertura, "transporDdrAbertura");
        this.parametrosAberturaDdr = Objects.requireNonNull(parametrosAberturaDdr, "parametrosAberturaDdr");
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

        LocalDate dataEncerramento = LocalDate.of(exercicio, 12, 31);

        // Apuração do resultado patrimonial (VPA/VPD contra a conta de PL) — RAZ-257.
        // Contas independentes das demais abaixo (classes 3/4), sem ordem obrigatória.
        if (contaResultadoApurado.isPresent()) {
            apurarResultadoPatrimonial.executar(
                    usuarioAutenticado, enteId, exercicio, periodo.id(), dataEncerramento, contaResultadoApurado.get());
        }

        // Inscrição de Restos a Pagar (RPNP e RPP) por fonte — RAZ-207.
        inscreverRP.executar(
                usuarioAutenticado, enteId, exercicio, periodo.id(), dataEncerramento, parametrosRP);

        // Encerramento das contas de controle orçamentário classes 5/6 — RAZ-258. Roda
        // depois da inscrição de RP (o saldo já transferido às contas de RP não é
        // reencerrado aqui).
        encerrarContasOrcamentarias.executar(
                usuarioAutenticado,
                enteId,
                exercicio,
                periodo.id(),
                dataEncerramento,
                parametrosEncerramentoOrcamentario);

        // Encerramento da DDR utilizada por fonte — RAZ-244 (IPC 03/STN §91). Contas
        // independentes das orçamentárias/RP acima (classes 7/8), sem ordem obrigatória
        // entre si. RAZ-266: parâmetros oficiais resolvidos por ente (conta_pcasp é
        // tenant-scoped).
        List<ParametroEncerramentoDdr> parametrosDdrDoEnte = parametrosDdr.para(enteId);
        encerrarDdr.executar(
                usuarioAutenticado, enteId, exercicio, periodo.id(), dataEncerramento, parametrosDdrDoEnte);

        PeriodoContabil encerrado = periodo.encerrarPeriodoExercicio(clock);

        boolean transicionou = periodoRepositorio.encerrar(encerrado);
        if (!transicionou) {
            throw new EncerramentoConflitanteException(periodo.id());
        }

        // Abertura do exercício seguinte — RAZ-259 (docs/15 §Preciso item 5, IPC 03/STN
        // item 30): transposição append-only de saldos patrimoniais permanentes em 1º/jan
        // (ex.: 2.3.7.1.1.01.00 → 2.3.7.1.1.02.00). Roda depois da transição condicional do
        // período — só abre o exercício seguinte se este encerramento realmente venceu a
        // corrida — e ainda dentro da mesma transação (ADR-0045: tudo ou nada).
        transporSaldosAbertura.executar(usuarioAutenticado, enteId, exercicio, parametrosAbertura);

        // Abertura da DDR: transposição do superávit financeiro por fonte — RAZ-244
        // (docs/15 §Preciso item 5, IPC 03/STN §96) — base do gate art. 42 no exercício
        // seguinte. Mesma janela transacional do item acima. RAZ-266: parâmetros oficiais
        // resolvidos por ente.
        List<ParametroTransposicaoDdrAbertura> parametrosAberturaDdrDoEnte = parametrosAberturaDdr.para(enteId);
        transporDdrAbertura.executar(usuarioAutenticado, enteId, exercicio, parametrosAberturaDdrDoEnte);

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
