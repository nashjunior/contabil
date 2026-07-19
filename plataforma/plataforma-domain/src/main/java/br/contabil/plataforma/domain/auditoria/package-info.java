/**
 * Ports <b>Auditoria</b> (doc 11 §Contratos; ADR-0005 append-only + hash-chain; ADR-0007
 * read models) — contrato estável com escrita e leitura <b>segregadas</b>
 * ({@link br.contabil.plataforma.domain.auditoria.AuditoriaEscrita} /
 * {@link br.contabil.plataforma.domain.auditoria.AuditoriaLeitura}). Camada PURA: sem
 * Spring/JPA. Implementação à parte (RAZ-6).
 */
package br.contabil.plataforma.domain.auditoria;
