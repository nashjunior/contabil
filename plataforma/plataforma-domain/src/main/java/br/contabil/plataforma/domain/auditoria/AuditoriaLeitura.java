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

    /**
     * Filtro de consulta — escopo por ente e recorte por tipo/ator/recurso/janela temporal.
     *
     * <p>{@code recurso} (ADR-0029 §3): recorte por URN de recurso (ex.: {@code
     * execucao:liquidacao:{id}}) para servir a trilha de um agregado específico sem
     * varrer a trilha inteira do ente — filtro imposto no servidor, ao lado de {@code
     * ente_id}. Opcional: os callers legados (janela por tipo/ator) seguem sem recurso.
     */
    record FiltroAuditoria(
            Optional<String> tipo,
            Optional<String> ator,
            Optional<String> recurso,
            JanelaConsulta janela) {

        /** Sem recorte por recurso (compat) — janela semântica. */
        public FiltroAuditoria(Optional<String> tipo, Optional<String> ator, JanelaConsulta janela) {
            this(tipo, ator, Optional.empty(), janela);
        }

        /** Sem recorte por recurso (compat) — janela por instantes. */
        public FiltroAuditoria(Optional<String> tipo, Optional<String> ator, Instant desde, Instant ate) {
            this(tipo, ator, Optional.empty(), new JanelaConsulta(desde, ate));
        }

        /** Com recorte por recurso (ADR-0029 §3) — janela por instantes. */
        public FiltroAuditoria(
                Optional<String> tipo, Optional<String> ator, Optional<String> recurso, Instant desde, Instant ate) {
            this(tipo, ator, recurso, new JanelaConsulta(desde, ate));
        }

        public FiltroAuditoria {
            Objects.requireNonNull(tipo, "tipo (Optional, nunca null)");
            Objects.requireNonNull(ator, "ator (Optional, nunca null)");
            Objects.requireNonNull(recurso, "recurso (Optional, nunca null)");
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
