package br.contabil.execucao.domain;

import java.time.LocalDate;
import java.util.Objects;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Resumo leve de uma liquidação na fila de aprovação (ADR-0029 §1) — só o que a
 * lista precisa, não a trilha inteira (essa é endpoint dedicado, §3).
 *
 * <p>O credor entra apenas como {@link CredorId} (referência opaca ao cadastro
 * estruturante): no estágio de aprovação — anterior ao pagamento — não há nome
 * nem CPF/CNPJ do credor materializado na execução (esses vivem no cadastro de
 * pessoas da plataforma, fora deste read model), então não há PII em claro a
 * mascarar aqui; a linha da fila é LGPD-safe por não expor identidade nominal
 * (04-lgpd). {@code numeroEmpenho}/{@code exercicioEmpenho} dão a referência
 * humana ("número") que a liquidação, só com UUID, não tem.
 */
public record ItemFilaAprovacao(
        LiquidacaoId id,
        EmpenhoId empenhoId,
        long numeroEmpenho,
        int exercicioEmpenho,
        CredorId credorId,
        Dinheiro valor,
        LocalDate dataCompetencia,
        StatusAprovacao statusAprovacao) {

    public ItemFilaAprovacao {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(empenhoId, "empenhoId");
        Objects.requireNonNull(credorId, "credorId");
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(dataCompetencia, "dataCompetencia");
        Objects.requireNonNull(statusAprovacao, "statusAprovacao");
    }
}
