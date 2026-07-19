package br.contabil.plataforma.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de tenant (ente/entidade). Base do isolamento multi-tenant —
 * o isolamento efetivo é aplicado via RLS na camada de infraestrutura.
 * Seed do shared kernel (RAZ-1).
 *
 * <p>Nome em inglês por ora destoa do vocabulário pt-BR do domínio (Ente) —
 * decisão adiada (RAZ-17) para quando a entidade {@code Ente} chegar ao
 * domínio: avaliar renomear para {@code EnteId} junto com esse trabalho, não
 * isoladamente (evita renomear duas vezes).
 */
public record TenantId(UUID valor) {

    public TenantId {
        Objects.requireNonNull(valor, "TenantId não pode ser nulo");
    }

    public static TenantId de(String uuid) {
        return new TenantId(UUID.fromString(uuid));
    }
}
