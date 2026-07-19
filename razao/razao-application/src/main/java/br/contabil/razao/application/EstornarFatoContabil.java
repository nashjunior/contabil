package br.contabil.razao.application;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.FatoContabil;
import java.time.Clock;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: corrige um fato consolidado por ESTORNO (Regra 3/4) — nunca por
 * UPDATE/DELETE. Cria um novo {@link FatoContabil} com lançamentos invertidos
 * (D↔C) que neutralizam o efeito líquido do original; o original permanece
 * intocado (motor-razao-partidas-dobradas.md).
 */
@Service
public class EstornarFatoContabil {

    private final FatoContabilRepository repositorio;
    private final ContadorFatoPort contadorFato;
    private final PeriodoContabilPort periodoContabil;
    private final Clock clock;

    public EstornarFatoContabil(
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
            TenantId enteId, UUID fatoOriginalId, LocalDate dataCompetencia, String historico, String origem) {
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
