package br.contabil.plataforma.domain.auditoria;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.consulta.ConsultaPaginada;
import br.contabil.plataforma.domain.consulta.JanelaConsulta;
import br.contabil.plataforma.domain.consulta.Paginacao;

/**
 * Contrato de <b>leitura</b> da trilha de auditoria (doc 11 §Contratos; ADR-0007
 * read models — a consulta é servida por read model derivado, não onera a escrita).
 *
 * <p>Segregada da {@link AuditoriaEscrita}: consultar não concede poder de escrever.
 */
public interface AuditoriaLeitura {

    /** Consulta eventos da trilha por filtro, na janela {@code [desde, ate)}. */
    Paginacao<EventoAuditoria> consultar(TenantId ente, ConsultaPaginada<FiltroAuditoria> consulta);

    /** Filtro de consulta — escopo por ente e recorte por tipo/ator/janela temporal. */
    record FiltroAuditoria(
            Optional<String> tipo,
            Optional<String> ator,
            JanelaConsulta janela) {
        public FiltroAuditoria(
                Optional<String> tipo,
                Optional<String> ator,
                Instant desde,
                Instant ate) {
            this(tipo, ator, new JanelaConsulta(desde, ate));
        }

        public FiltroAuditoria {
            Objects.requireNonNull(tipo, "tipo (Optional, nunca null)");
            Objects.requireNonNull(ator, "ator (Optional, nunca null)");
            Objects.requireNonNull(janela, "janela");
        }

        public Instant desde() {
            return janela.desde();
        }

        public Instant ate() {
            return janela.ate();
        }
    }
}
