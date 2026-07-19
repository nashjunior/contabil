package br.contabil.plataforma.domain.entrega;

import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.TenantId;
import java.util.Objects;
import java.util.UUID;

/**
 * Contrato de <b>Publicação/Entrega garantida</b> (doc 11 §Contratos; ADR-0004
 * outbox transacional + broker idempotente; ADR-0011 idempotência ponta a ponta).
 *
 * <p>Capacidade compartilhada (transparência, PNCP, SICONFI/TCE), <b>não</b> específica
 * de um consumidor. {@code enqueue} é <b>idempotente</b>: a mesma
 * {@link ChaveIdempotencia} devolve o mesmo {@link IdEntrega} (status
 * {@link StatusEntrega#DUPLICADO}), sem reenfileirar. O efeito externo grava o evento
 * no outbox na mesma transação do fato; o despacho é assíncrono, com retentativa e DLQ.
 *
 * <p>Nunca chamar saída externa síncrona dentro da transação do fato (ADR-0004).
 */
public interface ServicoEntrega {

    /**
     * Enfileira uma mensagem para entrega garantida (idempotente pela {@code chave}).
     *
     * @return o identificador de entrega — o mesmo para uma chave já vista.
     */
    IdEntrega enqueue(MensagemEntrega mensagem, ChaveIdempotencia chave);

    /** Situação de rastreio de uma entrega já enfileirada. */
    StatusEntrega status(IdEntrega id);

    // ---- Value objects do contrato --------------------------------------------

    /** Mensagem a entregar: escopo por ente, destino lógico, tipo e conteúdo (já serializado). */
    record MensagemEntrega(TenantId ente, String destino, String tipo, String conteudo) {
        public MensagemEntrega {
            Objects.requireNonNull(ente, "ente da mensagem");
            Objects.requireNonNull(destino, "destino da mensagem");
            Objects.requireNonNull(tipo, "tipo da mensagem");
            Objects.requireNonNull(conteudo, "conteúdo da mensagem");
        }
    }

    /** Identificador estável de uma entrega. */
    record IdEntrega(UUID valor) {
        public IdEntrega {
            Objects.requireNonNull(valor, "id de entrega");
        }
    }

    /**
     * Ciclo de vida da entrega (doc 11 §Contratos). {@code DUPLICADO} é resultado
     * normal do reenvio idempotente, não erro; {@code FALHA_PERMANENTE} indica DLQ.
     */
    enum StatusEntrega {
        ENFILEIRADO,
        DUPLICADO,
        RETENTANDO,
        ENTREGUE,
        FALHA_PERMANENTE
    }
}
