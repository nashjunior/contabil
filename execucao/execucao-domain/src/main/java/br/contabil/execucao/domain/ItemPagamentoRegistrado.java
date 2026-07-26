package br.contabil.execucao.domain;

import java.time.LocalDate;
import java.util.Objects;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Resumo leve de um pagamento registrado (RAZ-121). {@code beneficiario} fica
 * fora do resumo de propósito: é a única PII nominal do módulo de execução
 * (04-lgpd) e a listagem de registro não precisa dela — quem precisar do
 * beneficiário busca o pagamento pelo id ({@link
 * br.contabil.execucao.domain.repository.PagamentoRepository#buscarPorId}),
 * fronteira que já mascara/audita separadamente.
 */
public record ItemPagamentoRegistrado(
        PagamentoId id,
        LiquidacaoId liquidacaoId,
        Dinheiro valor,
        LocalDate dataCompetencia,
        NaturezaPagamento natureza,
        String historico) {

    public ItemPagamentoRegistrado {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(liquidacaoId, "liquidacaoId");
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(dataCompetencia, "dataCompetencia");
        Objects.requireNonNull(natureza, "natureza");
        Objects.requireNonNull(historico, "historico");
    }
}
