package br.contabil.razao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import br.contabil.razao.domain.repository.PeriodoContabilPort;

/**
 * Colaborador interno de {@link EncerrarExercicio}: abre o exercício seguinte ao mês
 * 13 transpondo saldos patrimoniais permanentes por lançamento append-only (RAZ-259,
 * docs/15-fechamento-contabil.md §Preciso item 5, IPC 03/STN rev. 2017 item 30/p.11).
 *
 * <p>Para cada {@link ParametroTransposicaoAbertura}, lê o saldo devedor líquido
 * acumulado da conta de origem e, se não-zero, gera em 1º/jan do exercício seguinte
 * um fato (Σdébito=Σcrédito) que zera a origem e lança a mesma quantia — mesma
 * natureza original do saldo — na conta de destino, preservando o sentido (superávit
 * credor / déficit devedor). Contas com saldo zero são ignoradas: nada a transpor.
 *
 * <p>Não tem RBAC próprio — o encerramento do exercício já é autorizado por
 * {@code EncerrarExercicio} (RBAC+MFA, ADR-0046) antes desta chamada. O período de
 * destino (mês 1 do exercício seguinte) precisa já existir e estar aberto — a
 * ausência propaga {@link br.contabil.razao.domain.PeriodoEncerradoException}, que
 * derruba toda a transação de encerramento (ADR-0045: tudo ou nada).
 */
public class TransporSaldosAbertura {

    private final FatoContabilRepository repositorio;
    private final ContadorFatoPort contadorFato;
    private final ConsultaSaldoPort consultaSaldo;
    private final PeriodoContabilPort periodoContabil;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public TransporSaldosAbertura(
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            ConsultaSaldoPort consultaSaldo,
            PeriodoContabilPort periodoContabil,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.contadorFato = Objects.requireNonNull(contadorFato, "contadorFato");
        this.consultaSaldo = Objects.requireNonNull(consultaSaldo, "consultaSaldo");
        this.periodoContabil = Objects.requireNonNull(periodoContabil, "periodoContabil");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Executa a abertura do exercício seguinte ao informado.
     *
     * @param sessao             sessão do usuário autenticado (para trilha de auditoria)
     * @param enteId             ente (tenant)
     * @param exercicioEncerrado exercício que acabou de ser encerrado — a abertura é em
     *                           1º/jan de {@code exercicioEncerrado + 1}
     * @param parametros         pares origem/destino a transpor
     * @return fatos de abertura gerados (um por parâmetro com saldo não-zero)
     */
    public List<FatoContabil> executar(
            Sessao sessao,
            TenantId enteId,
            int exercicioEncerrado,
            List<ParametroTransposicaoAbertura> parametros) {

        Objects.requireNonNull(sessao, "sessao");
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(parametros, "parametros");

        int exercicioSeguinte = exercicioEncerrado + 1;
        LocalDate dataAbertura = LocalDate.of(exercicioSeguinte, 1, 1);

        List<FatoContabil> transpostos = new ArrayList<>();

        for (ParametroTransposicaoAbertura param : parametros) {
            Dinheiro saldo = consultaSaldo.saldoDevedorLiquido(enteId, param.contaOrigem());
            if (saldo.isZero()) {
                continue;
            }

            boolean saldoCredor = saldo.valor().signum() < 0;
            Natureza naturezaOrigem = saldoCredor ? Natureza.DEBITO : Natureza.CREDITO;
            Natureza naturezaDestino = naturezaOrigem.inversa();
            Dinheiro valorTransposto = new Dinheiro(saldo.valor().abs());

            PeriodoContabilId periodoAbertura = periodoContabil.periodoAbertoPara(enteId, dataAbertura);
            long numeroSeq = contadorFato.proximoNumeroSeq(enteId);

            List<Lancamento> lancamentos = List.of(
                    Lancamento.de(param.contaOrigem(), naturezaOrigem, valorTransposto),
                    Lancamento.de(param.contaDestino(), naturezaDestino, valorTransposto));

            FatoContabil fato = FatoContabil.registrar(
                    enteId,
                    numeroSeq,
                    dataAbertura,
                    periodoAbertura,
                    TipoEvento.ABERTURA,
                    "Abertura do exercício %d — transposição de saldo".formatted(exercicioSeguinte),
                    "EncerrarExercicio",
                    lancamentos,
                    clock);

            repositorio.inserir(fato);

            auditoria.append(new EventoAuditoria(
                    enteId,
                    "razao_abertura_exercicio_transposicao",
                    sessao.titular().mascarado(),
                    "razao:fato_contabil:%s".formatted(fato.id().valor()),
                    Instant.now(clock),
                    Map.of(
                            "exercicio", String.valueOf(exercicioSeguinte),
                            "valor", valorTransposto.valor().toPlainString(),
                            "contaOrigem", param.contaOrigem().valor().toString(),
                            "contaDestino", param.contaDestino().valor().toString())));

            transpostos.add(fato);
        }

        return List.copyOf(transpostos);
    }
}
