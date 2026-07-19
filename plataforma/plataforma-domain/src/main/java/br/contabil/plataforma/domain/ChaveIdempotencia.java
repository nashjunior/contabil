package br.contabil.plataforma.domain;

import java.util.Objects;

/**
 * Chave de idempotência ponta a ponta (ADR-0011): identifica de forma estável uma
 * unidade lógica de entrada (inbox/ingestão) ou de saída (outbox/entrega).
 *
 * <p>Processar duas vezes a mesma chave tem efeito <b>uma vez</b> (<i>exactly-once</i>
 * lógico). É o mesmo conceito na ingestão (ADR-0011) e na publicação/entrega
 * (ADR-0004), por isso vive no shared kernel e é reusado pelos dois ports.
 *
 * <p>Seed dos contratos transversais (RAZ-8).
 */
public record ChaveIdempotencia(String valor) {

    public ChaveIdempotencia {
        Objects.requireNonNull(valor, "chave de idempotência não pode ser nula");
        if (valor.isBlank()) {
            throw new IllegalArgumentException("chave de idempotência não pode ser vazia");
        }
    }

    public static ChaveIdempotencia de(String valor) {
        return new ChaveIdempotencia(valor);
    }
}
