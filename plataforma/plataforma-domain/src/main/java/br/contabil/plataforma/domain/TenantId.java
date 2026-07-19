package br.contabil.plataforma.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de tenant (ente/entidade). Base do isolamento multi-tenant —
 * o isolamento efetivo é aplicado via RLS na camada de infraestrutura.
 * Seed do shared kernel (RAZ-1).
 */
public record TenantId(UUID valor) {

    public TenantId {
        Objects.requireNonNull(valor, "TenantId não pode ser nulo");
    }

    public static TenantId de(String uuid) {
        return new TenantId(UUID.fromString(uuid));
    }
}
