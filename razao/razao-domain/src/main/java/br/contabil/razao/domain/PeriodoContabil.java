package br.contabil.razao.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.Validacoes;

/**
 * Período contábil (mês 1–12; mês 13 = encerramento do exercício) — controla
 * se o razão aceita novos fatos (Regra 5, razao-contabil-schema.md). Sem
 * setters: a única transição de estado é {@link #encerrar}, sempre
 * {@code ABERTO -> ENCERRADO} (reabertura é fora do F1, docs/15 §Não preciso).
 */
public final class PeriodoContabil {

    private final PeriodoContabilId id;
    private final TenantId enteId;
    private final int exercicio;
    private final int mes;
    private final StatusPeriodo status;
    private final Optional<Instant> encerradoEm;

    private PeriodoContabil(
            PeriodoContabilId id,
            TenantId enteId,
            int exercicio,
            int mes,
            StatusPeriodo status,
            Optional<Instant> encerradoEm) {
        this.id = id;
        this.enteId = enteId;
        this.exercicio = exercicio;
        this.mes = mes;
        this.status = status;
        this.encerradoEm = encerradoEm;
    }

    /** Reconstitui um período já persistido, lido do repositório. */
    public static PeriodoContabil reidratar(
            PeriodoContabilId id,
            TenantId enteId,
            int exercicio,
            int mes,
            StatusPeriodo status,
            Optional<Instant> encerradoEm) {
        return new PeriodoContabil(
                Validacoes.exigirNaoNulo(id, "id"),
                Validacoes.exigirNaoNulo(enteId, "enteId"),
                exercicio,
                mes,
                Validacoes.exigirNaoNulo(status, "status"),
                Objects.requireNonNull(encerradoEm, "encerradoEm (Optional, nunca null)"));
    }

    /**
     * Transiciona {@code ABERTO -> ENCERRADO} (ADR-0045). Fail-fast em memória
     * antes de qualquer I/O — o repositório ainda impõe a mesma checagem como
     * transição condicional no banco (defesa em profundidade contra corrida,
     * mesmo padrão do {@code AprovarPagamento}/ADR-0023).
     *
     * @throws EncerramentoConflitanteException           o período já não está mais {@code ABERTO}
     * @throws PeriodoExercicioExigeApuracaoException mês 13 exige {@code EncerrarExercicio}
     */
    public PeriodoContabil encerrar(Clock clock) {
        if (status != StatusPeriodo.ABERTO) {
            throw new EncerramentoConflitanteException(id);
        }
        if (mes == 13) {
            throw new PeriodoExercicioExigeApuracaoException(id);
        }
        return new PeriodoContabil(id, enteId, exercicio, mes, StatusPeriodo.ENCERRADO, Optional.of(Instant.now(clock)));
    }

    /**
     * Transiciona o período de exercício (mês 13) para {@code ENCERRADO} após a inscrição
     * de Restos a Pagar (RAZ-207). Diferente de {@link #encerrar}, permite mês 13 —
     * chamado apenas por {@code EncerrarExercicio} depois de gerados todos os fatos de RP.
     *
     * @throws EncerramentoConflitanteException o período já não está {@code ABERTO}
     */
    public PeriodoContabil encerrarPeriodoExercicio(Clock clock) {
        if (mes != 13) {
            throw new PeriodoExercicioExigeApuracaoException(id);
        }
        if (status != StatusPeriodo.ABERTO) {
            throw new EncerramentoConflitanteException(id);
        }
        return new PeriodoContabil(id, enteId, exercicio, mes, StatusPeriodo.ENCERRADO, Optional.of(Instant.now(clock)));
    }

    public PeriodoContabilId id() {
        return id;
    }

    public TenantId enteId() {
        return enteId;
    }

    public int exercicio() {
        return exercicio;
    }

    public int mes() {
        return mes;
    }

    public StatusPeriodo status() {
        return status;
    }

    public Optional<Instant> encerradoEm() {
        return encerradoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PeriodoContabil outro)) {
            return false;
        }
        return id.equals(outro.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "PeriodoContabil[id=%s, enteId=%s, exercicio=%d, mes=%d, status=%s]"
                .formatted(id, enteId, exercicio, mes, status);
    }
}
