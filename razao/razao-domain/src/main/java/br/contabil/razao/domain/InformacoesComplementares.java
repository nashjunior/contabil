package br.contabil.razao.domain;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * As informações complementares (IC) da MSC <b>de nível partida</b> que um
 * {@link Lancamento} carrega — {@code FR, CO, NR, ND, FS, AI} (ADR-0050/ADR-0057).
 * Cada campo é <b>opcional e condicional por conta</b>: presente só nas partidas
 * cujas contas o exigem; a regra "conta X exige IC Y" é aplicada pela origem na
 * escrituração, não por esta VO (que apenas transporta o que foi capturado).
 *
 * <p>Não inclui a {@code PO} (Poder/Órgão) — fato-wide, mora no {@link FatoContabil} —
 * nem as derivadas {@code FP}/{@code DC}, que a projeção calcula de saldos
 * ({@link InformacaoComplementar.Origem}).
 *
 * <p>Absorve a {@link FonteRecurso} (FR) como primeiro campo, conforme previsto
 * pelo ADR-0054 ("o VO amplo pode absorver a coluna fonte_recurso depois"). Campos
 * {@code null} = IC ausente; nunca é {@code null} como um todo — use {@link #nenhuma()}.
 */
public record InformacoesComplementares(
        FonteRecurso fonteRecurso,
        ExecucaoOrcamentaria execucaoOrcamentaria,
        NaturezaReceita naturezaReceita,
        NaturezaDespesa naturezaDespesa,
        FuncaoSubfuncao funcaoSubfuncao,
        AnoInscricaoRp anoInscricaoRp)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final InformacoesComplementares NENHUMA =
            new InformacoesComplementares(null, null, null, null, null, null);

    /** A ausência de qualquer IC de partida (valor canônico, nunca {@code null}). */
    public static InformacoesComplementares nenhuma() {
        return NENHUMA;
    }

    /** Só a {@link FonteRecurso} (caminho FR/DDR já existente); {@code null} → {@link #nenhuma()}. */
    public static InformacoesComplementares apenasFonteRecurso(FonteRecurso fonteRecurso) {
        return fonteRecurso == null ? NENHUMA
                : new InformacoesComplementares(fonteRecurso, null, null, null, null, null);
    }

    /** Fábrica explícita; qualquer campo pode ser {@code null} (IC ausente). */
    public static InformacoesComplementares de(
            FonteRecurso fonteRecurso,
            ExecucaoOrcamentaria execucaoOrcamentaria,
            NaturezaReceita naturezaReceita,
            NaturezaDespesa naturezaDespesa,
            FuncaoSubfuncao funcaoSubfuncao,
            AnoInscricaoRp anoInscricaoRp) {
        InformacoesComplementares ic = new InformacoesComplementares(
                fonteRecurso, execucaoOrcamentaria, naturezaReceita, naturezaDespesa, funcaoSubfuncao, anoInscricaoRp);
        return ic.vazia() ? NENHUMA : ic;
    }

    /** Cópia com a {@link FonteRecurso} substituída (as demais IC preservadas). */
    public InformacoesComplementares comFonteRecurso(FonteRecurso novaFonte) {
        return new InformacoesComplementares(
                novaFonte, execucaoOrcamentaria, naturezaReceita, naturezaDespesa, funcaoSubfuncao, anoInscricaoRp);
    }

    /** {@code true} quando nenhuma IC de partida está presente. */
    public boolean vazia() {
        return fonteRecurso == null
                && execucaoOrcamentaria == null
                && naturezaReceita == null
                && naturezaDespesa == null
                && funcaoSubfuncao == null
                && anoInscricaoRp == null;
    }

    /**
     * As IC de partida efetivamente presentes — insumo da validação "conta exige IC"
     * (na escrituração) e da projeção MSC. Nunca inclui PO/FP/DC.
     */
    public Set<InformacaoComplementar> presentes() {
        EnumSet<InformacaoComplementar> presentes = EnumSet.noneOf(InformacaoComplementar.class);
        if (fonteRecurso != null) {
            presentes.add(InformacaoComplementar.FR);
        }
        if (execucaoOrcamentaria != null) {
            presentes.add(InformacaoComplementar.CO);
        }
        if (naturezaReceita != null) {
            presentes.add(InformacaoComplementar.NR);
        }
        if (naturezaDespesa != null) {
            presentes.add(InformacaoComplementar.ND);
        }
        if (funcaoSubfuncao != null) {
            presentes.add(InformacaoComplementar.FS);
        }
        if (anoInscricaoRp != null) {
            presentes.add(InformacaoComplementar.AI);
        }
        return Collections.unmodifiableSet(presentes);
    }
}
