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
import br.contabil.razao.domain.Balancete;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.LinhaBalancete;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.BalancetePort;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;

/**
 * Colaborador interno de {@link EncerrarExercicio}: apura o resultado patrimonial do
 * exercício (RAZ-257, docs/15-fechamento-contabil.md §Preciso item 2) — zera, num único
 * fato append-only (ADR-0045, {@link TipoEvento#ENCERRAMENTO}), todas as contas de VPD
 * (classe 3) e VPA (classe 4) com saldo, em contrapartida direta à conta de Patrimônio
 * Líquido "Superávits ou Déficits do Exercício" — sem conta transitória intermediária
 * (IPC 03/STN rev. 2017 itens 19-34; docs/15 §Fontes/a revalidar).
 *
 * <p>O saldo de cada conta vem do {@link BalancetePort} do mês 13, consultado ANTES de
 * qualquer fato de encerramento ser gerado nele — equivale ao saldo acumulado do
 * exercício inteiro (classes 3/4 só voltam a acumular depois desta apuração). O
 * zeramento usa o SINAL real do saldo atual (não a natureza "natural" da conta), para
 * nunca dobrar um saldo anômalo em vez de zerá-lo.
 *
 * <p>Não tem RBAC próprio — mesmo racional de {@link InscreverRestosAPagar}: o
 * encerramento do exercício já autoriza (RBAC+MFA, ADR-0046) antes desta chamada.
 */
public class ApurarResultadoPatrimonial {

    private static final int MES_ENCERRAMENTO_EXERCICIO = 13;
    private static final String PREFIXO_VPD = "3";
    private static final String PREFIXO_VPA = "4";

    private final FatoContabilRepository repositorio;
    private final ContadorFatoPort contadorFato;
    private final BalancetePort balancetePort;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public ApurarResultadoPatrimonial(
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            BalancetePort balancetePort,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.contadorFato = Objects.requireNonNull(contadorFato, "contadorFato");
        this.balancetePort = Objects.requireNonNull(balancetePort, "balancetePort");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Executa a apuração do resultado patrimonial para o exercício informado.
     *
     * @param contaResultadoApurado conta de PL "Superávits ou Déficits do Exercício"
     *                              (ex.: {@code 2.3.7.1.1.01.00}) — {@code [REVALIDAR]}
     *                              MCASP edição vigente antes de configurar em produção
     * @return o fato de apuração gerado; vazio se não há saldo de VPD/VPA a zerar
     */
    public Optional<FatoContabil> executar(
            Sessao sessao,
            TenantId enteId,
            int exercicio,
            PeriodoContabilId periodoId,
            LocalDate dataCompetencia,
            ContaContabilId contaResultadoApurado) {

        Objects.requireNonNull(sessao, "sessao");
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(periodoId, "periodoId");
        Objects.requireNonNull(dataCompetencia, "dataCompetencia");
        Objects.requireNonNull(contaResultadoApurado, "contaResultadoApurado");

        Balancete balancete = balancetePort.balancete(enteId, exercicio, MES_ENCERRAMENTO_EXERCICIO);

        List<Lancamento> lancamentos = new ArrayList<>();
        Dinheiro totalContrapartidaDebito = Dinheiro.zero();
        Dinheiro totalContrapartidaCredito = Dinheiro.zero();

        for (LinhaBalancete linha : balancete.linhas()) {
            if (!ehVpdOuVpa(linha.codigo()) || linha.saldoAtual().isZero()) {
                continue;
            }
            if (linha.saldoAtual().valor().signum() > 0) {
                lancamentos.add(Lancamento.de(linha.contaId(), Natureza.CREDITO, linha.saldoAtual()));
                totalContrapartidaDebito = totalContrapartidaDebito.somar(linha.saldoAtual());
            } else {
                Dinheiro valorAbsoluto = Dinheiro.zero().subtrair(linha.saldoAtual());
                lancamentos.add(Lancamento.de(linha.contaId(), Natureza.DEBITO, valorAbsoluto));
                totalContrapartidaCredito = totalContrapartidaCredito.somar(valorAbsoluto);
            }
        }

        if (lancamentos.isEmpty()) {
            return Optional.empty();
        }

        if (!totalContrapartidaDebito.isZero()) {
            lancamentos.add(Lancamento.de(contaResultadoApurado, Natureza.DEBITO, totalContrapartidaDebito));
        }
        if (!totalContrapartidaCredito.isZero()) {
            lancamentos.add(Lancamento.de(contaResultadoApurado, Natureza.CREDITO, totalContrapartidaCredito));
        }

        long numeroSeq = contadorFato.proximoNumeroSeq(enteId);

        FatoContabil fato = FatoContabil.registrar(
                enteId,
                numeroSeq,
                dataCompetencia,
                periodoId,
                TipoEvento.ENCERRAMENTO,
                "Apuração do resultado patrimonial (VPA/VPD) — exercício %d".formatted(exercicio),
                "EncerrarExercicio",
                lancamentos,
                clock);

        repositorio.inserir(fato);

        auditoria.append(new EventoAuditoria(
                enteId,
                "razao_apuracao_resultado_patrimonial",
                sessao.titular().mascarado(),
                "razao:fato_contabil:%s".formatted(fato.id().valor()),
                Instant.now(clock),
                Map.of(
                        "exercicio", String.valueOf(exercicio),
                        "totalContrapartidaDebito", totalContrapartidaDebito.valor().toPlainString(),
                        "totalContrapartidaCredito", totalContrapartidaCredito.valor().toPlainString(),
                        "contaResultadoApurado", contaResultadoApurado.valor().toString())));

        return Optional.of(fato);
    }

    private static boolean ehVpdOuVpa(String codigo) {
        return codigo.startsWith(PREFIXO_VPD) || codigo.startsWith(PREFIXO_VPA);
    }
}
