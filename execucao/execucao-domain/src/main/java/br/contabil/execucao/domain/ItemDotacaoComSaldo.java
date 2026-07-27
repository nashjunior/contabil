package br.contabil.execucao.domain;

import java.util.Objects;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Linha da listagem de dotações com saldo operacional inline (RAZ-148). Espelha
 * {@link SaldoDotacao}: autorizado, comprometido e disponível derivados no
 * servidor, sem exigir N chamadas de saldo pela UI.
 */
public record ItemDotacaoComSaldo(
        DotacaoId id,
        int exercicio,
        String classificacaoOrcamentaria,
        String fonteRecurso,
        UnidadeGestoraId unidadeGestoraId,
        Dinheiro valorAutorizado,
        Dinheiro valorComprometido) {

    public ItemDotacaoComSaldo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(classificacaoOrcamentaria, "classificacaoOrcamentaria");
        Objects.requireNonNull(fonteRecurso, "fonteRecurso");
        Objects.requireNonNull(unidadeGestoraId, "unidadeGestoraId");
        Objects.requireNonNull(valorAutorizado, "valorAutorizado");
        Objects.requireNonNull(valorComprometido, "valorComprometido");
        new SaldoDotacao(id, valorAutorizado, valorComprometido);
    }

    public Dinheiro saldoDisponivel() {
        return valorAutorizado.subtrair(valorComprometido);
    }
}
