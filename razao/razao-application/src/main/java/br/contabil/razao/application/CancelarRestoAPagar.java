package br.contabil.razao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.CancelamentoRestosAPagarInvalidoException;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.FatoContabilId;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;

/**
 * Caso de uso: cancela um Resto a Pagar inscrito (RAZ-207, docs/17-restos-a-pagar.md §Cancelamento).
 *
 * <p>Cancelamento é append-only: gera um novo fato com lançamentos invertidos
 * ({@link TipoEvento#CANCELAMENTO_RESTOS_A_PAGAR}) vinculado ao fato de inscrição original.
 * O fato de inscrição permanece intocado (razão append-only, Regra 3/4).
 *
 * <p>{@link ControleAcesso} — RBAC + MFA ({@link Acao#ESTORNAR} movimenta recurso, ADR-0016)
 * roda antes de qualquer leitura/escrita.
 *
 * <p>O {@code motivo} é obrigatório: toda cancelamento de RP exige justificativa auditável
 * (docs/17 §Cancelamento).
 */
public class CancelarRestoAPagar {

    private static final Recurso RECURSO_FATO_CONTABIL = new Recurso("razao:fato_contabil");

    private final ControleAcesso controleAcesso;
    private final FatoContabilRepository repositorio;
    private final ContadorFatoPort contadorFato;
    private final PeriodoContabilPort periodoContabil;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public CancelarRestoAPagar(
            ControleAcesso controleAcesso,
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            PeriodoContabilPort periodoContabil,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.contadorFato = Objects.requireNonNull(contadorFato, "contadorFato");
        this.periodoContabil = Objects.requireNonNull(periodoContabil, "periodoContabil");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Cancela o RP identificado pelo fato de inscrição, registrando o motivo na trilha.
     *
     * @param usuarioAutenticado sessão com RBAC+MFA já concluído
     * @param enteId             ente (tenant)
     * @param fatoInscricaoId    id do fato de {@link TipoEvento#INSCRICAO_RESTOS_A_PAGAR} a cancelar
     * @param dataCompetencia    data de competência do cancelamento (período aberto exigido)
     * @param motivo             justificativa obrigatória do cancelamento
     * @param origem             identificador da operação de origem (rastreabilidade)
     * @return fato de cancelamento gerado
     * @throws CancelamentoRestosAPagarInvalidoException o fato informado não é uma inscrição de RP
     */
    public FatoContabil executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            FatoContabilId fatoInscricaoId,
            LocalDate dataCompetencia,
            String motivo,
            String origem) {

        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_FATO_CONTABIL, Acao.ESTORNAR);

        FatoContabil inscricao = repositorio
                .buscarPorId(enteId, fatoInscricaoId)
                .orElseThrow(() -> new NoSuchElementException(
                        "inscrição de RP não encontrada: " + fatoInscricaoId));

        if (inscricao.tipoEvento() != TipoEvento.INSCRICAO_RESTOS_A_PAGAR) {
            throw new CancelamentoRestosAPagarInvalidoException(fatoInscricaoId, inscricao.tipoEvento());
        }

        var periodoId = periodoContabil.periodoAbertoPara(enteId, dataCompetencia);
        long numeroSeq = contadorFato.proximoNumeroSeq(enteId);

        FatoContabil cancelamento = FatoContabil.cancelarRestosAPagar(
                inscricao, numeroSeq, dataCompetencia, periodoId, motivo, origem, clock);

        repositorio.inserir(cancelamento);

        auditoria.append(new EventoAuditoria(
                enteId,
                "razao_cancelamento_restos_a_pagar",
                usuarioAutenticado.titular().mascarado(),
                "razao:fato_contabil:%s".formatted(cancelamento.id().valor()),
                Instant.now(clock),
                Map.of(
                        "fatoInscricaoId", fatoInscricaoId.valor().toString(),
                        "motivo", motivo,
                        "origem", origem,
                        "dataCompetencia", dataCompetencia.toString())));

        return cancelamento;
    }
}
