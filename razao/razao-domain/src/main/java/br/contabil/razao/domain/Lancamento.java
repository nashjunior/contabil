package br.contabil.razao.domain;

import java.util.Optional;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.Validacoes;

/**
 * Uma partida (débito ou crédito) sobre uma conta do PCASP. Membro interno do
 * agregado {@link FatoContabil} — não existe {@code Lancamento} sem um fato,
 * não é um agregado próprio (motor-razao-partidas-dobradas.md).
 *
 * <p>A {@link FonteRecurso} é uma dimensão <b>opcional e condicional por conta</b>
 * (ADR-0050/ADR-0054): obrigatória nas contas que carregam vinculação (controle
 * DDR classes 7/8 e as orçamentárias que a alimentam), ausente nas demais. O
 * modelo aqui só a carrega como opcional; a regra "conta X exige FR" é aplicada
 * pela origem/escrituração, não pela partida isolada.
 */
public final class Lancamento {

    private final LancamentoId id;
    private final ContaContabilId contaId;
    private final Natureza natureza;
    private final Dinheiro valor;
    private final FonteRecurso fonteRecurso;

    private Lancamento(
            LancamentoId id, ContaContabilId contaId, Natureza natureza, Dinheiro valor, FonteRecurso fonteRecurso) {
        this.id = id;
        this.contaId = contaId;
        this.natureza = natureza;
        this.valor = valor;
        this.fonteRecurso = fonteRecurso;
    }

    /** Cria um lançamento novo, validado, sem fonte de recursos (dimensão ausente). */
    public static Lancamento de(ContaContabilId contaId, Natureza natureza, Dinheiro valor) {
        return de(contaId, natureza, valor, null);
    }

    /**
     * Cria um lançamento novo, validado, com a {@link FonteRecurso} (vinculação)
     * da partida — {@code null} quando a conta não carrega FR.
     */
    public static Lancamento de(
            ContaContabilId contaId, Natureza natureza, Dinheiro valor, FonteRecurso fonteRecurso) {
        Validacoes.exigirNaoNulo(contaId, "contaId");
        Validacoes.exigirNaoNulo(natureza, "natureza");
        Validacoes.exigirNaoNulo(valor, "valor");
        if (valor.valor().signum() <= 0) {
            throw new LancamentoInvalidoException(
                    "valor do lançamento deve ser positivo (conta %s, natureza %s): %s"
                            .formatted(contaId, natureza, valor));
        }
        return new Lancamento(LancamentoId.novo(), contaId, natureza, valor, fonteRecurso);
    }

    /** Reconstitui um lançamento já persistido (sem fonte de recursos), preservando o id. */
    public static Lancamento reconstituir(
            LancamentoId id, ContaContabilId contaId, Natureza natureza, Dinheiro valor) {
        return reconstituir(id, contaId, natureza, valor, null);
    }

    /** Reconstitui um lançamento já persistido, preservando o id original e a {@link FonteRecurso}. */
    public static Lancamento reconstituir(
            LancamentoId id,
            ContaContabilId contaId,
            Natureza natureza,
            Dinheiro valor,
            FonteRecurso fonteRecurso) {
        return new Lancamento(
                Validacoes.exigirNaoNulo(id, "id"), contaId, natureza, valor, fonteRecurso);
    }

    /** Lançamento espelho para um estorno: mesma conta/valor/fonte, natureza invertida (D↔C). */
    Lancamento inverter() {
        return new Lancamento(LancamentoId.novo(), contaId, natureza.inversa(), valor, fonteRecurso);
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

    /** A fonte/destinação de recursos da partida, quando a conta a carrega (vazio caso contrário). */
    public Optional<FonteRecurso> fonteRecurso() {
        return Optional.ofNullable(fonteRecurso);
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
        return "Lancamento[id=%s, contaId=%s, natureza=%s, valor=%s, fonteRecurso=%s]"
                .formatted(id, contaId, natureza, valor, fonteRecurso);
    }
}
