package br.contabil.razao.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.NoSuchElementException;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.FatoContabilId;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;

/**
 * Caso de uso: corrige um fato consolidado por ESTORNO (Regra 3/4) — nunca por
 * UPDATE/DELETE. Cria um novo {@link FatoContabil} com lançamentos invertidos
 * (D↔C) que neutralizam o efeito líquido do original; o original permanece
 * intocado (motor-razao-partidas-dobradas.md).
 *
 * <p>{@link ControleAcesso} — RBAC + MFA (RAZ-33; ESTORNAR movimenta recurso) —
 * roda antes de qualquer leitura/escrita, inclusive antes de buscar o fato
 * original.
 */
public class EstornarFatoContabil {

    private static final Recurso RECURSO_FATO_CONTABIL = new Recurso("razao:fato_contabil");

    private final ControleAcesso controleAcesso;
    private final FatoContabilRepository repositorio;
    private final ContadorFatoPort contadorFato;
    private final PeriodoContabilPort periodoContabil;
    private final Clock clock;

    public EstornarFatoContabil(
            ControleAcesso controleAcesso,
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            PeriodoContabilPort periodoContabil,
            Clock clock) {
        this.controleAcesso = controleAcesso;
        this.repositorio = repositorio;
        this.contadorFato = contadorFato;
        this.periodoContabil = periodoContabil;
        this.clock = clock;
    }

    public FatoContabil executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            FatoContabilId fatoOriginalId,
            LocalDate dataCompetencia,
            String historico,
            String origem) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_FATO_CONTABIL, Acao.ESTORNAR);
        FatoContabil original = repositorio
                .buscarPorId(enteId, fatoOriginalId)
                .orElseThrow(() -> new NoSuchElementException(
                        "fato contábil não encontrado para estorno: " + fatoOriginalId));

        var periodoId = periodoContabil.periodoAbertoPara(enteId, dataCompetencia);
        long numeroSeq = contadorFato.proximoNumeroSeq(enteId);

        FatoContabil estorno =
                FatoContabil.estornar(original, numeroSeq, dataCompetencia, periodoId, historico, origem, clock);

        repositorio.inserir(estorno);
        return estorno;
    }
}
