package br.contabil.razao.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
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
    private final Clock clock;

    public RegistrarFatoContabil(
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
            LocalDate dataCompetencia,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            List<Lancamento> lancamentos) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_FATO_CONTABIL, Acao.CRIAR);
        FatoContabil.validarPartidasDobradas(lancamentos);

        var periodoId = periodoContabil.periodoAbertoPara(enteId, dataCompetencia);
        long numeroSeq = contadorFato.proximoNumeroSeq(enteId);

        FatoContabil fato = FatoContabil.registrar(
                enteId, numeroSeq, dataCompetencia, periodoId, tipoEvento, historico, origem, lancamentos, clock);

        repositorio.inserir(fato);
        return fato;
    }
}
