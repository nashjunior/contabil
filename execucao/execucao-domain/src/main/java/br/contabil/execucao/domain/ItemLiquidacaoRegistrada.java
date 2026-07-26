package br.contabil.execucao.domain;

import java.time.LocalDate;
import java.util.Objects;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Resumo leve de uma liquidação registrada (RAZ-121). Distinto de {@link
 * ItemFilaAprovacao}: aquele é o read model do <b>gate</b> (ADR-0029 §1, exclui
 * o autor por Regra 9); este é o <b>registro completo</b> — todas as liquidações
 * do ente no período, sem o recorte de segregação de funções, porque não serve
 * a um fluxo de aprovação, e sim a uma trilha de "o que já foi lançado".
 */
public record ItemLiquidacaoRegistrada(
        LiquidacaoId id,
        EmpenhoId empenhoId,
        Dinheiro valor,
        LocalDate dataCompetencia,
        String historico,
        StatusAprovacao statusAprovacao) {

    public ItemLiquidacaoRegistrada {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(empenhoId, "empenhoId");
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(dataCompetencia, "dataCompetencia");
        Objects.requireNonNull(historico, "historico");
        Objects.requireNonNull(statusAprovacao, "statusAprovacao");
    }
}
