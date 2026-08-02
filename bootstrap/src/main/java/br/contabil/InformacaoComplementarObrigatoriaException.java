package br.contabil;

import br.contabil.razao.domain.InformacaoComplementar;

/**
 * A conta PCASP referenciada pelo roteiro de contabilização (ADR-0021) exige uma
 * informação complementar (IC) da MSC que a partida não carrega — erro de
 * escrituração (ADR-0050/ADR-0057): a regra "conta X exige IC Y" é aplicada aqui,
 * na origem, nunca inferida depois sobre o histórico.
 */
public class InformacaoComplementarObrigatoriaException extends RuntimeException {

    public InformacaoComplementarObrigatoriaException(String codigoPcasp, InformacaoComplementar icExigida) {
        super("Conta PCASP %s exige a informação complementar %s (%s), ausente na partida"
                .formatted(codigoPcasp, icExigida, icExigida.descricao()));
    }
}
