package br.contabil.razao.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.Validacoes;

/**
 * Agregado raiz do razão contábil de dupla entrada — o evento (fato) que gera
 * {@link Lancamento}s balanceados sobre contas do PCASP (motor-razao-partidas-dobradas.md).
 * Sem setters e sem métodos {@code atualizar}/{@code excluir}: uma vez registrado,
 * só é corrigido por um novo fato de estorno (Regra 3/4, 05-regras-de-negocio.md).
 */
public final class FatoContabil {

    private final FatoContabilId id;
    private final TenantId enteId;
    private final long numeroSeq;
    private final LocalDate dataCompetencia;
    private final LocalDateTime dataHoraRegistro;
    private final PeriodoContabilId periodoId;
    private final TipoEvento tipoEvento;
    private final String historico;
    private final String origem;
    private final PoderOrgao poderOrgao;
    private final FatoContabilId fatoEstornadoId;
    private final List<Lancamento> lancamentos;

    private FatoContabil(
            FatoContabilId id,
            TenantId enteId,
            long numeroSeq,
            LocalDate dataCompetencia,
            LocalDateTime dataHoraRegistro,
            PeriodoContabilId periodoId,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            PoderOrgao poderOrgao,
            FatoContabilId fatoEstornadoId,
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
        this.poderOrgao = poderOrgao;
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
            PeriodoContabilId periodoId,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            List<Lancamento> lancamentos,
            Clock clock) {
        return registrar(
                enteId, numeroSeq, dataCompetencia, periodoId, tipoEvento, historico, origem, null, lancamentos, clock);
    }

    /**
     * Variante que carrega a IC {@code PO} (Poder/Órgão, unidade emitente) do evento
     * — fato-wide (ADR-0050/ADR-0057). {@code poderOrgao} {@code null} = fato sem
     * dimensão organizacional capturada.
     */
    public static FatoContabil registrar(
            TenantId enteId,
            long numeroSeq,
            LocalDate dataCompetencia,
            PeriodoContabilId periodoId,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            PoderOrgao poderOrgao,
            List<Lancamento> lancamentos,
            Clock clock) {
        return criar(
                FatoContabilId.novo(),
                enteId,
                numeroSeq,
                dataCompetencia,
                LocalDateTime.now(clock),
                periodoId,
                tipoEvento,
                historico,
                origem,
                poderOrgao,
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
            PeriodoContabilId periodoId,
            String historico,
            String origem,
            Clock clock) {
        Objects.requireNonNull(original, "fato original não pode ser nulo");
        List<Lancamento> invertidos = original.lancamentos.stream().map(Lancamento::inverter).toList();
        return criar(
                FatoContabilId.novo(),
                original.enteId,
                numeroSeq,
                dataCompetencia,
                LocalDateTime.now(clock),
                periodoId,
                TipoEvento.ESTORNO,
                historico,
                origem,
                original.poderOrgao,
                original.id,
                invertidos);
    }

    /**
     * Cancelamento de RP (RAZ-207): novo fato com lançamentos invertidos (D↔C) vinculado ao
     * fato de inscrição original. Diferente de {@link #estornar}, usa
     * {@link TipoEvento#CANCELAMENTO_RESTOS_A_PAGAR} para distinguir o evento no razão.
     *
     * @param inscricao fato de {@link TipoEvento#INSCRICAO_RESTOS_A_PAGAR} a cancelar
     */
    public static FatoContabil cancelarRestosAPagar(
            FatoContabil inscricao,
            long numeroSeq,
            LocalDate dataCompetencia,
            PeriodoContabilId periodoId,
            String historico,
            String origem,
            Clock clock) {
        Objects.requireNonNull(inscricao, "fato de inscrição não pode ser nulo");
        if (inscricao.tipoEvento != TipoEvento.INSCRICAO_RESTOS_A_PAGAR) {
            throw new CancelamentoRestosAPagarInvalidoException(inscricao.id, inscricao.tipoEvento);
        }
        List<Lancamento> invertidos = inscricao.lancamentos.stream().map(Lancamento::inverter).toList();
        return criar(
                FatoContabilId.novo(),
                inscricao.enteId,
                numeroSeq,
                dataCompetencia,
                LocalDateTime.now(clock),
                periodoId,
                TipoEvento.CANCELAMENTO_RESTOS_A_PAGAR,
                historico,
                origem,
                inscricao.poderOrgao,
                inscricao.id,
                invertidos);
    }

    /** Reconstitui um fato já consolidado (sem PO), lido do repositório — sem revalidar Σ=Σ. */
    public static FatoContabil reconstituir(
            FatoContabilId id,
            TenantId enteId,
            long numeroSeq,
            LocalDate dataCompetencia,
            LocalDateTime dataHoraRegistro,
            PeriodoContabilId periodoId,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            FatoContabilId fatoEstornadoId,
            List<Lancamento> lancamentos) {
        return reconstituir(
                id,
                enteId,
                numeroSeq,
                dataCompetencia,
                dataHoraRegistro,
                periodoId,
                tipoEvento,
                historico,
                origem,
                null,
                fatoEstornadoId,
                lancamentos);
    }

    /** Reconstitui um fato já consolidado, preservando a IC {@code PO} (Poder/Órgão) — sem revalidar Σ=Σ. */
    public static FatoContabil reconstituir(
            FatoContabilId id,
            TenantId enteId,
            long numeroSeq,
            LocalDate dataCompetencia,
            LocalDateTime dataHoraRegistro,
            PeriodoContabilId periodoId,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            PoderOrgao poderOrgao,
            FatoContabilId fatoEstornadoId,
            List<Lancamento> lancamentos) {
        return new FatoContabil(
                Validacoes.exigirNaoNulo(id, "id"),
                Validacoes.exigirNaoNulo(enteId, "enteId"),
                numeroSeq,
                Validacoes.exigirNaoNulo(dataCompetencia, "dataCompetencia"),
                Validacoes.exigirNaoNulo(dataHoraRegistro, "dataHoraRegistro"),
                Validacoes.exigirNaoNulo(periodoId, "periodoId"),
                Validacoes.exigirNaoNulo(tipoEvento, "tipoEvento"),
                Validacoes.exigirNaoNulo(historico, "histórico"),
                Validacoes.exigirNaoNulo(origem, "origem"),
                poderOrgao,
                fatoEstornadoId,
                List.copyOf(lancamentos));
    }

    private static FatoContabil criar(
            FatoContabilId id,
            TenantId enteId,
            long numeroSeq,
            LocalDate dataCompetencia,
            LocalDateTime dataHoraRegistro,
            PeriodoContabilId periodoId,
            TipoEvento tipoEvento,
            String historico,
            String origem,
            PoderOrgao poderOrgao,
            FatoContabilId fatoEstornadoId,
            List<Lancamento> lancamentos) {
        Validacoes.exigirNaoNulo(enteId, "enteId");
        Validacoes.exigirNaoNulo(dataCompetencia, "dataCompetencia");
        Validacoes.exigirNaoNulo(periodoId, "periodoId");
        Validacoes.exigirNaoNulo(tipoEvento, "tipoEvento");
        Validacoes.exigirNaoNulo(historico, "histórico");
        Validacoes.exigirNaoNulo(origem, "origem");
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
                poderOrgao,
                fatoEstornadoId,
                List.copyOf(lancamentos));
    }

    public FatoContabilId id() {
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

    public PeriodoContabilId periodoId() {
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

    /** A IC {@code PO} (Poder/Órgão, unidade emitente) do fato, quando capturada (vazio caso contrário). */
    public Optional<PoderOrgao> poderOrgao() {
        return Optional.ofNullable(poderOrgao);
    }

    public FatoContabilId fatoEstornadoId() {
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
