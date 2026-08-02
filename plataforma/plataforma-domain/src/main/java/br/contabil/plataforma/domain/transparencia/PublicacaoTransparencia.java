package br.contabil.plataforma.domain.transparencia;

import java.time.Instant;
import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;

/** Linha append-only do read model público de transparência ativa. */
public record PublicacaoTransparencia(
        TenantId enteId,
        String tipoEvento,
        String recurso,
        long sequencia,
        Instant publicadoEm,
        Instant publicarAte,
        String payloadJson) {

    public PublicacaoTransparencia {
        Objects.requireNonNull(enteId, "enteId");
        if (tipoEvento == null || tipoEvento.isBlank()) {
            throw new IllegalArgumentException("tipoEvento é obrigatório");
        }
        if (recurso == null || recurso.isBlank()) {
            throw new IllegalArgumentException("recurso é obrigatório");
        }
        if (sequencia < 1) {
            throw new IllegalArgumentException("sequência deve ser positiva");
        }
        Objects.requireNonNull(publicadoEm, "publicadoEm");
        Objects.requireNonNull(publicarAte, "publicarAte");
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("payloadJson é obrigatório");
        }
    }
}
