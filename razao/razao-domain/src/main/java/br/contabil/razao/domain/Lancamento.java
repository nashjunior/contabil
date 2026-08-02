package br.contabil.razao.domain;

import java.util.Optional;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.Validacoes;

/**
 * Uma partida (débito ou crédito) sobre uma conta do PCASP. Membro interno do
 * agregado {@link FatoContabil} — não existe {@code Lancamento} sem um fato,
 * não é um agregado próprio (motor-razao-partidas-dobradas.md).
 *
 * <p>As {@link InformacoesComplementares} (IC de partida: FR/CO/NR/ND/FS/AI) são
 * dimensões <b>opcionais e condicionais por conta</b> (ADR-0050/ADR-0054/ADR-0057):
 * obrigatórias nas contas que as carregam (ex. FR nas de controle DDR classes 7/8),
 * ausentes nas demais. O modelo aqui só as transporta como opcionais; a regra
 * "conta X exige IC Y" é aplicada pela origem/escrituração, não pela partida isolada.
 * A {@code PO} (Poder/Órgão) é fato-wide e mora no {@link FatoContabil}.
 */
public final class Lancamento {

    private final LancamentoId id;
    private final ContaContabilId contaId;
    private final Natureza natureza;
    private final Dinheiro valor;
    private final InformacoesComplementares ic;

    private Lancamento(
            LancamentoId id,
            ContaContabilId contaId,
            Natureza natureza,
            Dinheiro valor,
            InformacoesComplementares ic) {
        this.id = id;
        this.contaId = contaId;
        this.natureza = natureza;
        this.valor = valor;
        this.ic = ic == null ? InformacoesComplementares.nenhuma() : ic;
    }

    /** Cria um lançamento novo, validado, sem informações complementares (dimensões ausentes). */
    public static Lancamento de(ContaContabilId contaId, Natureza natureza, Dinheiro valor) {
        return de(contaId, natureza, valor, InformacoesComplementares.nenhuma());
    }

    /**
     * Cria um lançamento novo, validado, só com a {@link FonteRecurso} (vinculação)
     * da partida — {@code null} quando a conta não carrega FR. Atalho do caminho
     * FR/DDR já existente (RAZ-225/ADR-0054).
     */
    public static Lancamento de(
            ContaContabilId contaId, Natureza natureza, Dinheiro valor, FonteRecurso fonteRecurso) {
        return de(contaId, natureza, valor, InformacoesComplementares.apenasFonteRecurso(fonteRecurso));
    }

    /** Cria um lançamento novo, validado, com o conjunto de {@link InformacoesComplementares} da partida. */
    public static Lancamento de(
            ContaContabilId contaId, Natureza natureza, Dinheiro valor, InformacoesComplementares ic) {
        Validacoes.exigirNaoNulo(contaId, "contaId");
        Validacoes.exigirNaoNulo(natureza, "natureza");
        Validacoes.exigirNaoNulo(valor, "valor");
        if (valor.valor().signum() <= 0) {
            throw new LancamentoInvalidoException(
                    "valor do lançamento deve ser positivo (conta %s, natureza %s): %s"
                            .formatted(contaId, natureza, valor));
        }
        return new Lancamento(LancamentoId.novo(), contaId, natureza, valor, ic);
    }

    /** Reconstitui um lançamento já persistido (sem IC), preservando o id. */
    public static Lancamento reconstituir(
            LancamentoId id, ContaContabilId contaId, Natureza natureza, Dinheiro valor) {
        return reconstituir(id, contaId, natureza, valor, InformacoesComplementares.nenhuma());
    }

    /** Reconstitui um lançamento já persistido, preservando o id original e só a {@link FonteRecurso}. */
    public static Lancamento reconstituir(
            LancamentoId id,
            ContaContabilId contaId,
            Natureza natureza,
            Dinheiro valor,
            FonteRecurso fonteRecurso) {
        return reconstituir(
                id, contaId, natureza, valor, InformacoesComplementares.apenasFonteRecurso(fonteRecurso));
    }

    /** Reconstitui um lançamento já persistido, preservando o id original e as {@link InformacoesComplementares}. */
    public static Lancamento reconstituir(
            LancamentoId id,
            ContaContabilId contaId,
            Natureza natureza,
            Dinheiro valor,
            InformacoesComplementares ic) {
        return new Lancamento(Validacoes.exigirNaoNulo(id, "id"), contaId, natureza, valor, ic);
    }

    /** Lançamento espelho para um estorno: mesma conta/valor/IC, natureza invertida (D↔C). */
    Lancamento inverter() {
        return new Lancamento(LancamentoId.novo(), contaId, natureza.inversa(), valor, ic);
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

    /** As informações complementares de partida (FR/CO/NR/ND/FS/AI) — nunca {@code null}. */
    public InformacoesComplementares informacoesComplementares() {
        return ic;
    }

    /** A fonte/destinação de recursos da partida, quando a conta a carrega (vazio caso contrário). */
    public Optional<FonteRecurso> fonteRecurso() {
        return Optional.ofNullable(ic.fonteRecurso());
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
        return "Lancamento[id=%s, contaId=%s, natureza=%s, valor=%s, ic=%s]"
                .formatted(id, contaId, natureza, valor, ic);
    }
}
