package br.contabil.plataforma.domain.auditoria;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;

/**
 * Evento da trilha de auditoria (doc 11 §Contratos; ADR-0005 append-only + hash-chain;
 * Decreto 10.540/2020 arts. 9º e 12).
 *
 * <p>"Evento é o próprio registro": a instância é o dado auditável — imutável por
 * construção. Cobre inclusive leitura/exportação de PII (evento
 * {@code acesso_dado_pessoal}). {@code detalhes} é um mapa imutável de contexto
 * (nunca PII em claro — usar o serviço de Mascaramento antes de registrar).
 */
public record EventoAuditoria(
        TenantId ente,
        String tipo,
        String ator,
        String recurso,
        Instant momento,
        Map<String, String> detalhes) {

    public EventoAuditoria {
        Objects.requireNonNull(ente, "ente do evento");
        Objects.requireNonNull(tipo, "tipo do evento");
        Objects.requireNonNull(ator, "ator do evento");
        Objects.requireNonNull(recurso, "recurso do evento");
        Objects.requireNonNull(momento, "momento do evento");
        detalhes = Map.copyOf(Objects.requireNonNullElse(detalhes, Map.of()));
    }
}
