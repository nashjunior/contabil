package br.contabil.razao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ConsultaSaldoPort;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;

/**
 * Colaborador interno de {@link EncerrarExercicio}: encerra as contas de controle
 * orçamentário PCASP classes 5/6 no mês 13 (RAZ-258, docs/15-fechamento-contabil.md
 * §Preciso item 3; IPC 03/STN rev. 2017, itens 35-63, revalidado na RAZ-232).
 *
 * <p>Para cada {@link ParametroEncerramentoOrcamentario}, lê o saldo devedor líquido
 * <b>agregado</b> (sem fonte — diferente da DDR/RP, cujas contas classes 7/8 carregam
 * {@code FonteRecurso}; as classes 5/6 desta base não carregam) da conta de controle e
 * acumula, num único fato append-only Σdébito=Σcrédito, o lançamento que a zera contra a
 * conta de contrapartida do orçamento aprovado. Contas já zeradas são ignoradas. <b>Nunca</b>
 * cria conta de "resultado orçamentário": o superávit/déficit orçamentário é read-model
 * (Balanço Orçamentário, ADR-0047), nunca fato de razão — cada par só transfere o mesmo
 * valor entre as duas contas, sem sobra nem conta residual.
 *
 * <p>Não tem RBAC próprio — o encerramento do exercício já é autorizado por
 * {@code EncerrarExercicio} (RBAC+MFA, ADR-0046) antes desta chamada.
 *
 * <p>Os códigos PCASP concretos ficam nos {@link ParametroEncerramentoOrcamentario}
 * injetados pelo bootstrap — {@code [REVALIDAR]} MCASP edição vigente antes de
 * configurar em produção.
 */
public class EncerrarContasOrcamentarias {

    private final FatoContabilRepository repositorio;
    private final ContadorFatoPort contadorFato;
    private final ConsultaSaldoPort consultaSaldo;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public EncerrarContasOrcamentarias(
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            ConsultaSaldoPort consultaSaldo,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.contadorFato = Objects.requireNonNull(contadorFato, "contadorFato");
        this.consultaSaldo = Objects.requireNonNull(consultaSaldo, "consultaSaldo");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Executa o encerramento das contas de controle orçamentário para o exercício informado.
     *
     * @param sessao          sessão do usuário autenticado (para trilha de auditoria)
     * @param enteId          ente (tenant)
     * @param exercicio       ano de exercício sendo encerrado
     * @param periodoId       período contábil (mês 13) já verificado como aberto
     * @param dataCompetencia data do encerramento — normalmente 31/12 do exercício
     * @param parametros      pares conta de controle / contrapartida (classes 5/6,
     *                        {@code [REVALIDAR]} MCASP)
     * @return o fato de encerramento gerado; vazio se não há parâmetros configurados ou
     *         todas as contas já estavam zeradas (nada a encerrar)
     */
    public Optional<FatoContabil> executar(
            Sessao sessao,
            TenantId enteId,
            int exercicio,
            PeriodoContabilId periodoId,
            LocalDate dataCompetencia,
            List<ParametroEncerramentoOrcamentario> parametros) {

        Objects.requireNonNull(sessao, "sessao");
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(periodoId, "periodoId");
        Objects.requireNonNull(dataCompetencia, "dataCompetencia");
        Objects.requireNonNull(parametros, "parametros");

        List<Lancamento> lancamentos = new ArrayList<>();

        for (ParametroEncerramentoOrcamentario param : parametros) {
            Dinheiro saldo = consultaSaldo.saldoDevedorLiquido(enteId, param.contaControle());
            if (saldo.isZero()) {
                continue;
            }

            // saldoDevedorLiquido é Σdébito-Σcrédito: negativo para contas de controle
            // naturalmente credoras (ex.: execução da receita, 6.2.1.x). O valor do
            // lançamento é sempre a MAGNITUDE — a natureza já vem fixada pelo parâmetro
            // (por par de conta, conforme o PCASP), nunca inferida do sinal observado.
            Dinheiro valor = new Dinheiro(saldo.valor().abs());

            Natureza naturezaControle = param.naturezaContrapartida() == Natureza.CREDITO
                    ? Natureza.DEBITO
                    : Natureza.CREDITO;

            lancamentos.add(Lancamento.de(param.contaControle(), naturezaControle, valor));
            lancamentos.add(Lancamento.de(param.contaContrapartida(), param.naturezaContrapartida(), valor));
        }

        if (lancamentos.isEmpty()) {
            return Optional.empty();
        }

        long numeroSeq = contadorFato.proximoNumeroSeq(enteId);

        FatoContabil fato = FatoContabil.registrar(
                enteId,
                numeroSeq,
                dataCompetencia,
                periodoId,
                TipoEvento.ENCERRAMENTO,
                "Encerramento das contas de controle orçamentário (classes 5/6) — exercício %d".formatted(exercicio),
                "EncerrarExercicio",
                lancamentos,
                clock);

        repositorio.inserir(fato);

        auditoria.append(new EventoAuditoria(
                enteId,
                "razao_encerramento_contas_orcamentarias",
                sessao.titular().mascarado(),
                "razao:fato_contabil:%s".formatted(fato.id().valor()),
                Instant.now(clock),
                Map.of(
                        "exercicio", String.valueOf(exercicio),
                        "contasEncerradas", String.valueOf(lancamentos.size() / 2))));

        return Optional.of(fato);
    }
}
