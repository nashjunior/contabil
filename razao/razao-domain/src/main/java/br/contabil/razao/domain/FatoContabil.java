package br.contabil.razao.domain;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado raiz do razão contábil de dupla entrada — o evento (fato) que gera
 * {@link Lancamento}s balanceados sobre contas do PCASP (motor-razao-partidas-dobradas.md).
 * Sem setters e sem métodos {@code atualizar}/{@code excluir}: uma vez registrado,
 * só é corrigido por um novo fato de estorno (Regra 3/4, 05-regras-de-negocio.md).
 */
public final class FatoContabil {

    private final UUID id;
    private final TenantId enteId;
    private final long numeroSeq;
    private final LocalDate dataCompetencia;
    private final LocalDateTime dataHoraRegistro;
    private final UUID periodoId;
    private final TipoEvento tipoEvento;
    private final String historico;
    private final String origem;
    private final UUID fatoEstornadoId;
    private final List<Lancamento> lancamentos;

    private FatoContabil(
            UUID id,
            TenantId enteId,
            long numeroSeq,
            LocalDate dataCompetencia,
            LocalDateTime dataHoraRegistro,
            UUID periodoId,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            UUID fatoEstornadoId,
            List<Lancamento> lancamentos) {
        this.id = id;
        this.enteId = enteId;
        this.numeroSeq = numeroSeq;
        this.dataCompetencia = dataCompetencia;
        this.dataHoraRegistro = dataHoraRegistro;
        this.periodoId = periodoId;
        this.tipoEvento = tipoEvento;
        this.historico = historico;
        this.origem = origem;
        this.fatoEstornadoId = fatoEstornadoId;
        this.lancamentos = lancamentos;
    }

    /**
     * Verificação fail-fast da Regra 8 — soma(D) = soma(C) — feita ANTES de
     * qualquer I/O (sem período/numero_seq ainda obtidos). O use case chama
     * isto antes de abrir a transação; {@link #registrar} e {@link #estornar}
     * repetem a checagem ao montar o agregado (defesa em profundidade).
     */
    public static void validarPartidasDobradas(List<Lancamento> lancamentos) {
        if (lancamentos == null || lancamentos.size() < 2) {
            throw new PartidasNaoBalanceadasException(
                    "um fato contábil precisa de ao menos 2 lançamentos (débito e crédito)");
        }
        Dinheiro somaDebito = Dinheiro.zero();
        Dinheiro somaCredito = Dinheiro.zero();
        for (Lancamento lancamento : lancamentos) {
            if (lancamento.natureza() == Natureza.DEBITO) {
                somaDebito = somaDebito.somar(lancamento.valor());
            } else {
                somaCredito = somaCredito.somar(lancamento.valor());
            }
        }
        if (!somaDebito.equals(somaCredito)) {
            throw new PartidasNaoBalanceadasException(somaDebito, somaCredito);
        }
    }

    /**
     * Registra um novo fato contábil. {@code numeroSeq} e {@code periodoId} já
     * foram obtidos pelo use case (contador com lock de linha, período aberto)
     * antes desta chamada — o agregado não sabe de banco.
     */
    public static FatoContabil registrar(
            TenantId enteId,
            long numeroSeq,
            LocalDate dataCompetencia,
            UUID periodoId,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            List<Lancamento> lancamentos,
            Clock clock) {
        return criar(
                UUID.randomUUID(),
                enteId,
                numeroSeq,
                dataCompetencia,
                LocalDateTime.now(clock),
                periodoId,
                tipoEvento,
                historico,
                origem,
                null,
                lancamentos);
    }

    /**
     * Correção por estorno (Regra 3/4): um NOVO fato, com lançamentos invertidos
     * (D↔C) que neutralizam o efeito líquido do original — nunca UPDATE/DELETE
     * no original. O estorno também precisa fechar Σ=Σ; como os lançamentos são
     * só a inversão de natureza dos originais, isso vale automaticamente.
     */
    public static FatoContabil estornar(
            FatoContabil original,
            long numeroSeq,
            LocalDate dataCompetencia,
            UUID periodoId,
            String historico,
            String origem,
            Clock clock) {
        Objects.requireNonNull(original, "fato original não pode ser nulo");
        List<Lancamento> invertidos = original.lancamentos.stream().map(Lancamento::inverter).toList();
        return criar(
                UUID.randomUUID(),
                original.enteId,
                numeroSeq,
                dataCompetencia,
                LocalDateTime.now(clock),
                periodoId,
                TipoEvento.ESTORNO,
                historico,
                origem,
                original.id,
                invertidos);
    }

    /** Reconstitui um fato já consolidado, lido do repositório — sem revalidar Σ=Σ. */
    public static FatoContabil reconstituir(
            UUID id,
            TenantId enteId,
            long numeroSeq,
            LocalDate dataCompetencia,
            LocalDateTime dataHoraRegistro,
            UUID periodoId,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            UUID fatoEstornadoId,
            List<Lancamento> lancamentos) {
        return new FatoContabil(
                Objects.requireNonNull(id, "id não pode ser nulo"),
                Objects.requireNonNull(enteId, "enteId não pode ser nulo"),
                numeroSeq,
                Objects.requireNonNull(dataCompetencia, "dataCompetencia não pode ser nula"),
                Objects.requireNonNull(dataHoraRegistro, "dataHoraRegistro não pode ser nula"),
                Objects.requireNonNull(periodoId, "periodoId não pode ser nulo"),
                Objects.requireNonNull(tipoEvento, "tipoEvento não pode ser nulo"),
                Objects.requireNonNull(historico, "histórico não pode ser nulo"),
                Objects.requireNonNull(origem, "origem não pode ser nula"),
                fatoEstornadoId,
                List.copyOf(lancamentos));
    }

    private static FatoContabil criar(
            UUID id,
            TenantId enteId,
            long numeroSeq,
            LocalDate dataCompetencia,
            LocalDateTime dataHoraRegistro,
            UUID periodoId,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            UUID fatoEstornadoId,
            List<Lancamento> lancamentos) {
        Objects.requireNonNull(enteId, "enteId não pode ser nulo");
        Objects.requireNonNull(dataCompetencia, "dataCompetencia não pode ser nula");
        Objects.requireNonNull(periodoId, "periodoId não pode ser nulo");
        Objects.requireNonNull(tipoEvento, "tipoEvento não pode ser nulo");
        Objects.requireNonNull(historico, "histórico não pode ser nulo");
        Objects.requireNonNull(origem, "origem não pode ser nula");
        validarPartidasDobradas(lancamentos);
        return new FatoContabil(
                id,
                enteId,
                numeroSeq,
                dataCompetencia,
                dataHoraRegistro,
                periodoId,
                tipoEvento,
                historico,
                origem,
                fatoEstornadoId,
                List.copyOf(lancamentos));
    }

    public UUID id() {
        return id;
    }

    public TenantId enteId() {
        return enteId;
    }

    public long numeroSeq() {
        return numeroSeq;
    }

    public LocalDate dataCompetencia() {
        return dataCompetencia;
    }

    public LocalDateTime dataHoraRegistro() {
        return dataHoraRegistro;
    }

    public UUID periodoId() {
        return periodoId;
    }

    public TipoEvento tipoEvento() {
        return tipoEvento;
    }

    public String historico() {
        return historico;
    }

    public String origem() {
        return origem;
    }

    public UUID fatoEstornadoId() {
        return fatoEstornadoId;
    }

    public boolean isEstorno() {
        return fatoEstornadoId != null;
    }

    public List<Lancamento> lancamentos() {
        return lancamentos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FatoContabil outro)) {
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
        return "FatoContabil[id=%s, enteId=%s, numeroSeq=%d, tipoEvento=%s]"
                .formatted(id, enteId, numeroSeq, tipoEvento);
    }
}
