package br.contabil.razao.application;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.TipoEvento;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: registra um novo fato contábil (motor-razao-partidas-dobradas.md).
 *
 * <p>Ordem do fluxo: (1) valida Σdébito=Σcrédito ANTES de qualquer I/O — falha
 * rápido sem sequer abrir transação para o erro mais comum; (2) dentro da
 * transação, confere período aberto (Regra 5); (3) obtém o próximo
 * {@code numero_seq} (lock de linha, mesma transação — se algo falhar daqui pra
 * frente, o rollback desfaz o incremento junto, sem buraco); (4) monta o
 * agregado (revalida Σ=Σ, defesa em profundidade); (5) persiste fato +
 * lançamentos atomicamente.
 */
@Service
public class RegistrarFatoContabil {

    private final FatoContabilRepository repositorio;
    private final ContadorFatoPort contadorFato;
    private final PeriodoContabilPort periodoContabil;
    private final Clock clock;

    public RegistrarFatoContabil(
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            PeriodoContabilPort periodoContabil,
            Clock clock) {
        this.repositorio = repositorio;
        this.contadorFato = contadorFato;
        this.periodoContabil = periodoContabil;
        this.clock = clock;
    }

    @Transactional
    public FatoContabil executar(
            TenantId enteId,
            LocalDate dataCompetencia,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            List<Lancamento> lancamentos) {
        FatoContabil.validarPartidasDobradas(lancamentos);

        var periodoId = periodoContabil.periodoAbertoPara(enteId, dataCompetencia);
        long numeroSeq = contadorFato.proximoNumeroSeq(enteId);

        FatoContabil fato = FatoContabil.registrar(
                enteId, numeroSeq, dataCompetencia, periodoId, tipoEvento, historico, origem, lancamentos, clock);

        repositorio.inserir(fato);
        return fato;
    }
}
