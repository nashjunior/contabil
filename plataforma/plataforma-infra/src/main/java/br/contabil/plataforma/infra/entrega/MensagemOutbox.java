package br.contabil.plataforma.infra.entrega;

import java.util.Objects;

import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.IdEntrega;

/** Linha operacional do outbox reclamada pelo worker. */
public record MensagemOutbox(
        IdEntrega id,
        TenantId ente,
        ChaveIdempotencia chave,
        String destino,
        String tipo,
        String conteudo,
        int tentativas) {

    public MensagemOutbox {
        Objects.requireNonNull(id, "id da entrega");
        Objects.requireNonNull(ente, "ente da entrega");
        Objects.requireNonNull(chave, "chave de idempotência");
        Objects.requireNonNull(destino, "destino");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(conteudo, "conteúdo");
        if (tentativas < 0) {
            throw new IllegalArgumentException("tentativas não pode ser negativo");
        }
    }
}
