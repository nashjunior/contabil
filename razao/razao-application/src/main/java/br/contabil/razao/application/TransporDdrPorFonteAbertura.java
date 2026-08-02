package br.contabil.razao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.DisponibilidadePorFontePort;
import br.contabil.razao.domain.repository.DisponibilidadePorFontePort.SaldoPorFonte;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;

/**
 * Colaborador interno de {@link EncerrarExercicio}: transpõe o superávit financeiro da
 * DDR (classes 7/8) por fonte para o exercício seguinte (RAZ-244,
 * docs/15-fechamento-contabil.md §Preciso item 5, IPC 03/STN rev. 2017 §96) — base do
 * gate art. 42 ({@code VerificarDisponibilidadeArt42}, RAZ-243) no novo exercício.
 *
 * <p>Estruturalmente a junção de {@link EncerrarDdrPorFonte} (leitura por fonte via
 * {@link DisponibilidadePorFontePort}) com {@link TransporSaldosAbertura} (abertura em
 * 1º/jan do exercício seguinte, {@link TipoEvento#ABERTURA}): para cada
 * {@link ParametroTransposicaoDdrAbertura}, lê o saldo devedor líquido por fonte na
 * conta de origem e, se não-zero, gera em 1º/jan um fato (Σdébito=Σcrédito) que zera a
 * origem e lança a mesma quantia na conta de destino, mantendo a fonte na partida.
 * Fontes com saldo zero são ignoradas.
 *
 * <p>Não tem RBAC próprio — o encerramento do exercício já é autorizado por
 * {@code EncerrarExercicio} (RBAC+MFA, ADR-0046) antes desta chamada. O período de
 * destino (mês 1 do exercício seguinte) precisa já existir e estar aberto — a ausência
 * propaga {@link br.contabil.razao.domain.PeriodoEncerradoException}, que derruba toda
 * a transação de encerramento (ADR-0045: tudo ou nada).
 *
 * <p>Os códigos PCASP concretos ficam nos {@link ParametroTransposicaoDdrAbertura}
 * injetados pelo bootstrap — {@code [REVALIDAR]} MCASP edição vigente antes de produção.
 */
public class TransporDdrPorFonteAbertura {

    private final FatoContabilRepository repositorio;
    private final ContadorFatoPort contadorFato;
    private final DisponibilidadePorFontePort disponibilidadePorFonte;
    private final PeriodoContabilPort periodoContabil;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public TransporDdrPorFonteAbertura(
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            DisponibilidadePorFontePort disponibilidadePorFonte,
            PeriodoContabilPort periodoContabil,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.contadorFato = Objects.requireNonNull(contadorFato, "contadorFato");
        this.disponibilidadePorFonte = Objects.requireNonNull(disponibilidadePorFonte, "disponibilidadePorFonte");
        this.periodoContabil = Objects.requireNonNull(periodoContabil, "periodoContabil");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Executa a transposição do superávit financeiro da DDR por fonte para o exercício
     * seguinte ao informado.
     *
     * @param sessao             sessão do usuário autenticado (para trilha de auditoria)
     * @param enteId             ente (tenant)
     * @param exercicioEncerrado exercício que acabou de ser encerrado — a abertura é em
     *                           1º/jan de {@code exercicioEncerrado + 1}
     * @param parametros         configuração das contas de origem/destino da DDR por fonte
     *                           ({@code [REVALIDAR]} MCASP)
     * @return fatos de abertura gerados (um por fonte com saldo positivo por parâmetro)
     */
    public List<FatoContabil> executar(
            Sessao sessao,
            TenantId enteId,
            int exercicioEncerrado,
            List<ParametroTransposicaoDdrAbertura> parametros) {

        Objects.requireNonNull(sessao, "sessao");
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(parametros, "parametros");

        int exercicioSeguinte = exercicioEncerrado + 1;
        LocalDate dataAbertura = LocalDate.of(exercicioSeguinte, 1, 1);

        List<FatoContabil> transpostos = new ArrayList<>();

        for (ParametroTransposicaoDdrAbertura param : parametros) {
            List<SaldoPorFonte> saldos = disponibilidadePorFonte
                    .consultarSaldoPorFonte(enteId, List.of(param.contaOrigem()));

            for (SaldoPorFonte saldo : saldos) {
                if (saldo.saldoDevedorLiquido().isZero()) {
                    continue;
                }

                Natureza naturezaOrigem = param.naturezaDestino() == Natureza.CREDITO
                        ? Natureza.DEBITO
                        : Natureza.CREDITO;

                List<Lancamento> lancamentos = List.of(
                        Lancamento.de(
                                param.contaOrigem(),
                                naturezaOrigem,
                                saldo.saldoDevedorLiquido(),
                                saldo.fonte()),
                        Lancamento.de(
                                param.contaDestino(),
                                param.naturezaDestino(),
                                saldo.saldoDevedorLiquido(),
                                saldo.fonte()));

                PeriodoContabilId periodoAbertura = periodoContabil.periodoAbertoPara(enteId, dataAbertura);
                long numeroSeq = contadorFato.proximoNumeroSeq(enteId);

                FatoContabil fato = FatoContabil.registrar(
                        enteId,
                        numeroSeq,
                        dataAbertura,
                        periodoAbertura,
                        TipoEvento.ABERTURA,
                        "Abertura DDR superávit financeiro exercício %d — fonte %s"
                                .formatted(exercicioSeguinte, saldo.fonte().codigo()),
                        "EncerrarExercicio",
                        lancamentos,
                        clock);

                repositorio.inserir(fato);

                auditoria.append(new EventoAuditoria(
                        enteId,
                        "razao_abertura_ddr_por_fonte",
                        sessao.titular().mascarado(),
                        "razao:fato_contabil:%s".formatted(fato.id().valor()),
                        Instant.now(clock),
                        Map.of(
                                "exercicio", String.valueOf(exercicioSeguinte),
                                "fonte", saldo.fonte().codigo(),
                                "valor", saldo.saldoDevedorLiquido().valor().toPlainString(),
                                "contaOrigem", param.contaOrigem().valor().toString(),
                                "contaDestino", param.contaDestino().valor().toString())));

                transpostos.add(fato);
            }
        }

        return List.copyOf(transpostos);
    }
}
