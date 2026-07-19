package br.contabil.plataforma.domain.auditoria;

import br.contabil.plataforma.domain.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Contrato de <b>leitura</b> da trilha de auditoria (doc 11 §Contratos; ADR-0007
 * read models — a consulta é servida por read model derivado, não onera a escrita).
 *
 * <p>Segregada da {@link AuditoriaEscrita}: consultar não concede poder de escrever.
 */
public interface AuditoriaLeitura {

    /** Consulta eventos da trilha por filtro, na janela {@code [desde, ate)}. */
    List<EventoAuditoria> consultar(FiltroAuditoria filtro);

    /** Filtro de consulta — escopo por ente e recorte por tipo/ator/janela temporal. */
    record FiltroAuditoria(
            Optional<TenantId> ente,
            Optional<String> tipo,
            Optional<String> ator,
            Instant desde,
            Instant ate) {
        public FiltroAuditoria {
            Objects.requireNonNull(ente, "ente (Optional, nunca null)");
            Objects.requireNonNull(tipo, "tipo (Optional, nunca null)");
            Objects.requireNonNull(ator, "ator (Optional, nunca null)");
            Objects.requireNonNull(desde, "início da janela");
            Objects.requireNonNull(ate, "fim da janela");
        }
    }
}
