package br.contabil.razao.domain;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.Validacoes;

/**
 * Uma partida (débito ou crédito) sobre uma conta do PCASP. Membro interno do
 * agregado {@link FatoContabil} — não existe {@code Lancamento} sem um fato,
 * não é um agregado próprio (motor-razao-partidas-dobradas.md).
 */
public final class Lancamento {

    private final LancamentoId id;
    private final ContaContabilId contaId;
    private final Natureza natureza;
    private final Dinheiro valor;

    private Lancamento(LancamentoId id, ContaContabilId contaId, Natureza natureza, Dinheiro valor) {
        this.id = id;
        this.contaId = contaId;
        this.natureza = natureza;
        this.valor = valor;
    }

    /** Cria um lançamento novo, validado — usado ao montar um fato para registrar. */
    public static Lancamento de(ContaContabilId contaId, Natureza natureza, Dinheiro valor) {
        Validacoes.exigirNaoNulo(contaId, "contaId");
        Validacoes.exigirNaoNulo(natureza, "natureza");
        Validacoes.exigirNaoNulo(valor, "valor");
        if (valor.valor().signum() <= 0) {
            throw new LancamentoInvalidoException(
                    "valor do lançamento deve ser positivo (conta %s, natureza %s): %s"
                            .formatted(contaId, natureza, valor));
        }
        return new Lancamento(LancamentoId.novo(), contaId, natureza, valor);
    }

    /** Reconstitui um lançamento já persistido, preservando o id original. */
    public static Lancamento reconstituir(
            LancamentoId id, ContaContabilId contaId, Natureza natureza, Dinheiro valor) {
        return new Lancamento(
                Validacoes.exigirNaoNulo(id, "id"), contaId, natureza, valor);
    }

    /** Lançamento espelho para um estorno: mesma conta/valor, natureza invertida (D↔C). */
    Lancamento inverter() {
        return new Lancamento(LancamentoId.novo(), contaId, natureza.inversa(), valor);
    }

    public LancamentoId id() {
        return id;
    }

    public ContaContabilId contaId() {
        return contaId;
    }

    public Natureza natureza() {
        return natureza;
    }

    public Dinheiro valor() {
        return valor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Lancamento outro)) {
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
        return "Lancamento[id=%s, contaId=%s, natureza=%s, valor=%s]".formatted(id, contaId, natureza, valor);
    }
}
