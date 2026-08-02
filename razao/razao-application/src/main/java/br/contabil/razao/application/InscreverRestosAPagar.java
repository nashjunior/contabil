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

/**
 * Colaborador interno de {@link EncerrarExercicio}: inscreve os Restos a Pagar (RP)
 * no razão ao encerrar o exercício (RAZ-207, docs/17-restos-a-pagar.md).
 *
 * <p>Para cada {@link ParametroInscricaoRP} recebido, lê o saldo devedor líquido
 * por fonte na conta de origem e gera um fato de inscrição (Σdébito=Σcrédito, append-only)
 * que move o saldo para a conta de destino mantendo a {@link br.contabil.razao.domain.FonteRecurso}
 * na partida. Fontes com saldo zero são ignoradas.
 *
 * <p>Não tem RBAC próprio — o encerramento do exercício já é autorizado por
 * {@code EncerrarExercicio} (RBAC+MFA, ADR-0046) antes desta chamada.
 *
 * <p>Os códigos PCASP concretos ficam nos {@link ParametroInscricaoRP} injetados
 * pelo bootstrap — {@code [REVALIDAR]} MCASP edição vigente antes de produção.
 */
public class InscreverRestosAPagar {

    private final FatoContabilRepository repositorio;
    private final ContadorFatoPort contadorFato;
    private final DisponibilidadePorFontePort disponibilidadePorFonte;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public InscreverRestosAPagar(
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            DisponibilidadePorFontePort disponibilidadePorFonte,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.contadorFato = Objects.requireNonNull(contadorFato, "contadorFato");
        this.disponibilidadePorFonte = Objects.requireNonNull(disponibilidadePorFonte, "disponibilidadePorFonte");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Executa a inscrição de RP para o exercício informado.
     *
     * @param sessao          sessão do usuário autenticado (para trilha de auditoria)
     * @param enteId          ente (tenant)
     * @param exercicio       ano de exercício sendo encerrado
     * @param periodoId       período contábil (mês 13) já verificado como aberto
     * @param dataCompetencia data do encerramento — normalmente 31/12 do exercício
     * @param parametros      configuração das contas de origem/destino por tipo de RP
     *                        (RPNP e RPP — {@code [REVALIDAR]} MCASP)
     * @return fatos de inscrição gerados (um por fonte com saldo positivo por parâmetro)
     */
    public List<FatoContabil> executar(
            Sessao sessao,
            TenantId enteId,
            int exercicio,
            PeriodoContabilId periodoId,
            LocalDate dataCompetencia,
            List<ParametroInscricaoRP> parametros) {

        Objects.requireNonNull(sessao, "sessao");
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(periodoId, "periodoId");
        Objects.requireNonNull(dataCompetencia, "dataCompetencia");
        Objects.requireNonNull(parametros, "parametros");

        List<FatoContabil> inscritos = new ArrayList<>();

        for (ParametroInscricaoRP param : parametros) {
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

                long numeroSeq = contadorFato.proximoNumeroSeq(enteId);

                FatoContabil fato = FatoContabil.registrar(
                        enteId,
                        numeroSeq,
                        dataCompetencia,
                        periodoId,
                        TipoEvento.INSCRICAO_RESTOS_A_PAGAR,
                        "Inscrição de RP exercício %d — fonte %s".formatted(exercicio, saldo.fonte().codigo()),
                        "EncerrarExercicio",
                        lancamentos,
                        clock);

                repositorio.inserir(fato);

                auditoria.append(new EventoAuditoria(
                        enteId,
                        "razao_inscricao_restos_a_pagar",
                        sessao.titular().mascarado(),
                        "razao:fato_contabil:%s".formatted(fato.id().valor()),
                        Instant.now(clock),
                        Map.of(
                                "exercicio", String.valueOf(exercicio),
                                "fonte", saldo.fonte().codigo(),
                                "valor", saldo.saldoDevedorLiquido().valor().toPlainString(),
                                "contaOrigem", param.contaOrigem().valor().toString(),
                                "contaDestino", param.contaDestino().valor().toString())));

                inscritos.add(fato);
            }
        }

        return List.copyOf(inscritos);
    }
}
