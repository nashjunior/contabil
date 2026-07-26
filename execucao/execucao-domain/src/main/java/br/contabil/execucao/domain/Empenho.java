package br.contabil.execucao.domain;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado de empenho (Lei 4.320/1964 art. 58-60): compromete crédito da
 * dotação para um credor e gera um fato contábil append-only no razão.
 *
 * <p>F1 cobre só o registro inicial (emissão) — reforço, anulação e o ciclo de
 * vida completo (execucao-orcamentaria-despesa.md §Ciclo de vida) ficam para
 * issues próprias que consomem este agregado.
 */
public final class Empenho {

    private final EmpenhoId id;
    private final TenantId enteId;
    private final long numeroSequencial;
    private final int exercicio;
    private final TipoEmpenho tipo;
    private final DotacaoId dotacaoId;
    private final UUID credorId;
    private final UUID unidadeGestoraId;
    private final UUID contratoId;
    private final Dinheiro valor;
    private final LocalDate dataFato;
    private final String classificacaoOrcamentaria;
    private final String fonteRecurso;
    private final String historico;
    private final UUID fatoContabilId;

    private Empenho(
            EmpenhoId id,
            TenantId enteId,
            long numeroSequencial,
            int exercicio,
            TipoEmpenho tipo,
            DotacaoId dotacaoId,
            UUID credorId,
            UUID unidadeGestoraId,
            UUID contratoId,
            Dinheiro valor,
            LocalDate dataFato,
            String classificacaoOrcamentaria,
            String fonteRecurso,
            String historico,
            UUID fatoContabilId) {
        this.id = id;
        this.enteId = enteId;
        this.numeroSequencial = numeroSequencial;
        this.exercicio = exercicio;
        this.tipo = tipo;
        this.dotacaoId = dotacaoId;
        this.credorId = credorId;
        this.unidadeGestoraId = unidadeGestoraId;
        this.contratoId = contratoId;
        this.valor = valor;
        this.dataFato = dataFato;
        this.classificacaoOrcamentaria = classificacaoOrcamentaria;
        this.fonteRecurso = fonteRecurso;
        this.historico = historico;
        this.fatoContabilId = fatoContabilId;
    }

    public static Empenho registrar(
            EmpenhoId id,
            TenantId enteId,
            long numeroSequencial,
            int exercicio,
            TipoEmpenho tipo,
            DotacaoId dotacaoId,
            UUID credorId,
            UUID unidadeGestoraId,
            UUID contratoId,
            Dinheiro valor,
            LocalDate dataFato,
            String classificacaoOrcamentaria,
            String fonteRecurso,
            String historico,
            UUID fatoContabilId) {
        validarValor(valor, "valor do empenho");
        return new Empenho(
                Objects.requireNonNull(id, "id não pode ser nulo"),
                Objects.requireNonNull(enteId, "enteId não pode ser nulo"),
                numeroSequencial,
                exercicio,
                Objects.requireNonNull(tipo, "tipo não pode ser nulo"),
                Objects.requireNonNull(dotacaoId, "dotacaoId não pode ser nulo"),
                Objects.requireNonNull(credorId, "credorId não pode ser nulo"),
                Objects.requireNonNull(unidadeGestoraId, "unidadeGestoraId não pode ser nulo"),
                contratoId,
                valor,
                Objects.requireNonNull(dataFato, "dataFato não pode ser nula"),
                textoObrigatorio(classificacaoOrcamentaria, "classificação orçamentária"),
                textoObrigatorio(fonteRecurso, "fonte de recurso"),
                textoObrigatorio(historico, "histórico"),
                Objects.requireNonNull(fatoContabilId, "fatoContabilId não pode ser nulo"));
    }

    public static void validarValor(Dinheiro valor, String campo) {
        Objects.requireNonNull(valor, campo + " não pode ser nulo");
        if (valor.compareTo(Dinheiro.zero()) <= 0) {
            throw new ExecucaoInvalidaException("valor_invalido", campo + " deve ser positivo");
        }
    }

    static String textoObrigatorio(String valor, String campo) {
        Objects.requireNonNull(valor, campo + " não pode ser nulo");
        if (valor.isBlank()) {
            throw new IllegalArgumentException(campo + " não pode ser vazio");
        }
        return valor;
    }

    public EmpenhoId id() {
        return id;
    }

    public TenantId enteId() {
        return enteId;
    }

    public long numeroSequencial() {
        return numeroSequencial;
    }

    public int exercicio() {
        return exercicio;
    }

    public TipoEmpenho tipo() {
        return tipo;
    }

    public DotacaoId dotacaoId() {
        return dotacaoId;
    }

    public UUID credorId() {
        return credorId;
    }

    public UUID unidadeGestoraId() {
        return unidadeGestoraId;
    }

    public UUID contratoId() {
        return contratoId;
    }

    public Dinheiro valor() {
        return valor;
    }

    public LocalDate dataFato() {
        return dataFato;
    }

    public String classificacaoOrcamentaria() {
        return classificacaoOrcamentaria;
    }

    public String fonteRecurso() {
        return fonteRecurso;
    }

    public String historico() {
        return historico;
    }

    public UUID fatoContabilId() {
        return fatoContabilId;
    }
}
