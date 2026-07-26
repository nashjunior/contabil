package br.contabil.execucao.domain;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import java.util.Objects;

/**
 * Agregado de dotação (crédito orçamentário, Lei 4.320/1964 arts. 34/40) — a
 * base que o {@link Empenho} consome (art. 59). F1 cobre só a carga inicial
 * (Fixação: LOA/créditos adicionais); mutar {@code valorAutorizado} por
 * reforço orçamentário fica para issue própria — a V3 migration
 * (RAZ-66) já não concede {@code UPDATE} em {@code dotacao} por ora.
 *
 * <p>O saldo disponível NÃO é um campo desta classe: é derivado
 * (valorAutorizado − soma dos empenhos vinculados) e consultado via
 * {@link SaldoDotacao} ({@code SaldosExecucaoPort#saldoDotacao}), sob lock de
 * linha nesta dotação — é ali que mora o bloqueio de empenhar além do
 * disponível (execucao-orcamentaria-despesa.md §Dois saldos, dois donos).
 * Este agregado representa só a autorização orçamentária em si.
 */
public final class Dotacao {

    private final DotacaoId id;
    private final TenantId enteId;
    private final int exercicio;
    private final String classificacaoOrcamentaria;
    private final String fonteRecurso;
    private final UnidadeGestoraId unidadeGestoraId;
    private final Dinheiro valorAutorizado;

    private Dotacao(
            DotacaoId id,
            TenantId enteId,
            int exercicio,
            String classificacaoOrcamentaria,
            String fonteRecurso,
            UnidadeGestoraId unidadeGestoraId,
            Dinheiro valorAutorizado) {
        this.id = id;
        this.enteId = enteId;
        this.exercicio = exercicio;
        this.classificacaoOrcamentaria = classificacaoOrcamentaria;
        this.fonteRecurso = fonteRecurso;
        this.unidadeGestoraId = unidadeGestoraId;
        this.valorAutorizado = valorAutorizado;
    }

    public static Dotacao registrar(
            DotacaoId id,
            TenantId enteId,
            int exercicio,
            String classificacaoOrcamentaria,
            String fonteRecurso,
            UnidadeGestoraId unidadeGestoraId,
            Dinheiro valorAutorizado) {
        validarValorAutorizado(valorAutorizado);
        return new Dotacao(
                Objects.requireNonNull(id, "id não pode ser nulo"),
                Objects.requireNonNull(enteId, "enteId não pode ser nulo"),
                exercicio,
                textoObrigatorio(classificacaoOrcamentaria, "classificação orçamentária"),
                textoObrigatorio(fonteRecurso, "fonte de recurso"),
                Objects.requireNonNull(unidadeGestoraId, "unidadeGestoraId não pode ser nulo"),
                valorAutorizado);
    }

    public static void validarValorAutorizado(Dinheiro valorAutorizado) {
        Objects.requireNonNull(valorAutorizado, "valorAutorizado não pode ser nulo");
        if (valorAutorizado.compareTo(Dinheiro.zero()) <= 0) {
            throw new ExecucaoInvalidaException("valor_invalido", "valorAutorizado da dotação deve ser positivo");
        }
    }

    static String textoObrigatorio(String valor, String campo) {
        Objects.requireNonNull(valor, campo + " não pode ser nulo");
        if (valor.isBlank()) {
            throw new IllegalArgumentException(campo + " não pode ser vazio");
        }
        return valor;
    }

    public DotacaoId id() {
        return id;
    }

    public TenantId enteId() {
        return enteId;
    }

    public int exercicio() {
        return exercicio;
    }

    public String classificacaoOrcamentaria() {
        return classificacaoOrcamentaria;
    }

    public String fonteRecurso() {
        return fonteRecurso;
    }

    public UnidadeGestoraId unidadeGestoraId() {
        return unidadeGestoraId;
    }

    public Dinheiro valorAutorizado() {
        return valorAutorizado;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Dotacao outra)) {
            return false;
        }
        return id.equals(outra.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Dotacao[id=%s, enteId=%s, exercicio=%d, valorAutorizado=%s]"
                .formatted(id, enteId, exercicio, valorAutorizado);
    }
}
