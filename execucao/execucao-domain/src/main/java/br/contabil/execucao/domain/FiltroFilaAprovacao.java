package br.contabil.execucao.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Filtro da fila de aprovação (ADR-0029 §1). {@code statusAprovacao} é o eixo
 * do gate ({@code pendente|aprovada|devolvida} — o estado forte do agregado,
 * ADR-0023), distinto do {@code status} derivado do saldo (§6.4). Os demais
 * campos são recortes opcionais expostos como query params. A segregação da
 * Regra 9 NÃO mora aqui: é imposta no servidor pela query a partir do CPF do
 * solicitante da sessão, não é um filtro que o cliente escolhe (ADR-0029 §2).
 */
public record FiltroFilaAprovacao(
        StatusAprovacao statusAprovacao,
        Optional<String> fonte,
        Optional<LocalDate> dataInicio,
        Optional<LocalDate> dataFim,
        Optional<Dinheiro> valorMin,
        Optional<Dinheiro> valorMax) {

    public FiltroFilaAprovacao {
        Objects.requireNonNull(statusAprovacao, "statusAprovacao");
        Objects.requireNonNull(fonte, "fonte (Optional, nunca null)");
        Objects.requireNonNull(dataInicio, "dataInicio (Optional, nunca null)");
        Objects.requireNonNull(dataFim, "dataFim (Optional, nunca null)");
        Objects.requireNonNull(valorMin, "valorMin (Optional, nunca null)");
        Objects.requireNonNull(valorMax, "valorMax (Optional, nunca null)");
    }

    /** Filtro mínimo: só o eixo do gate, sem recortes adicionais. */
    public static FiltroFilaAprovacao porStatus(StatusAprovacao statusAprovacao) {
        return new FiltroFilaAprovacao(
                statusAprovacao, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
