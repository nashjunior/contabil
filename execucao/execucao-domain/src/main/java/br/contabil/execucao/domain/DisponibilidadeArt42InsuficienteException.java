package br.contabil.execucao.domain;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * ADR-0044: dentro dos dois últimos quadrimestres do mandato, {@code RegistrarEmpenho}
 * (e, na inscrição do encerramento, {@code InscreverRestosAPagar}) recusa a obrigação
 * cuja fonte de recursos não tenha disponibilidade de caixa líquida suficiente —
 * vedação do art. 42 da LRF. Sem compensação entre fontes (ADR-0044/ADR-0054).
 */
public final class DisponibilidadeArt42InsuficienteException extends ExecucaoInvalidaException {

    public DisponibilidadeArt42InsuficienteException(String fonteRecurso, Dinheiro valor, Dinheiro disponivel) {
        super(
                "disponibilidade_art42_insuficiente",
                "fonte de recursos %s sem disponibilidade de caixa suficiente para %s (disponível: %s)"
                        .formatted(fonteRecurso, valor, disponivel));
    }
}
