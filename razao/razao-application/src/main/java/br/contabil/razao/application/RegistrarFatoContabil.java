package br.contabil.razao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.PoderOrgao;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;

/**
 * Caso de uso: registra um novo fato contábil (motor-razao-partidas-dobradas.md).
 *
 * <p>Ordem do fluxo: (1) {@link ControleAcesso} — RBAC + MFA (RAZ-33; CRIAR
 * movimenta recurso) ANTES de qualquer outra coisa, inclusive da validação de
 * negócio; (2) valida Σdébito=Σcrédito ANTES de qualquer I/O — falha rápido sem
 * sequer abrir transação para o erro mais comum; (3) dentro da transação,
 * confere período aberto (Regra 5); (4) obtém o próximo {@code numero_seq}
 * (lock de linha, mesma transação — se algo falhar daqui pra frente, o
 * rollback desfaz o incremento junto, sem buraco); (5) monta o agregado
 * (revalida Σ=Σ, defesa em profundidade); (6) persiste fato + lançamentos
 * atomicamente.
 */
public class RegistrarFatoContabil {

    private static final Recurso RECURSO_FATO_CONTABIL = new Recurso("razao:fato_contabil");

    private final ControleAcesso controleAcesso;
    private final FatoContabilRepository repositorio;
    private final ContadorFatoPort contadorFato;
    private final PeriodoContabilPort periodoContabil;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public RegistrarFatoContabil(
            ControleAcesso controleAcesso,
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            PeriodoContabilPort periodoContabil,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.controleAcesso = controleAcesso;
        this.repositorio = repositorio;
        this.contadorFato = contadorFato;
        this.periodoContabil = periodoContabil;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public FatoContabil executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            LocalDate dataCompetencia,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            List<Lancamento> lancamentos) {
        return executar(usuarioAutenticado, enteId, dataCompetencia, tipoEvento, historico, origem, null, lancamentos);
    }

    public FatoContabil executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            LocalDate dataCompetencia,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            PoderOrgao poderOrgao,
            List<Lancamento> lancamentos) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_FATO_CONTABIL, Acao.CRIAR);
        FatoContabil.validarPartidasDobradas(lancamentos);

        var periodoId = periodoContabil.periodoAbertoPara(enteId, dataCompetencia);
        long numeroSeq = contadorFato.proximoNumeroSeq(enteId);

        FatoContabil fato = FatoContabil.registrar(
                enteId,
                numeroSeq,
                dataCompetencia,
                periodoId,
                tipoEvento,
                historico,
                origem,
                poderOrgao,
                lancamentos,
                clock);

        repositorio.inserir(fato);

        // Trilha [OBRIGATÓRIO] (docs/04-fluxos §7): toda escrituração de fato é auditada AQUI,
        // no use case, não por conta do chamador — assim qualquer borda futura do razão herda a
        // trilha (o caminho da execução já audita a própria operação em RegistrarEmpenho etc.).
        auditoria.append(new EventoAuditoria(
                enteId,
                "razao_fato_contabil_registrado",
                usuarioAutenticado.titular().mascarado(),
                "razao:fato_contabil:%s".formatted(fato.id().valor()),
                Instant.now(clock),
                Map.of(
                        "tipoEvento", tipoEvento.name(),
                        "origem", origem,
                        "dataCompetencia", dataCompetencia.toString())));
        return fato;
    }
}
