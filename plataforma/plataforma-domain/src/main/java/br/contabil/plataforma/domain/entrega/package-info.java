/**
 * Port <b>Publicação/Entrega garantida</b> (doc 11 §Contratos; ADR-0004 outbox; ADR-0011
 * idempotência) — contrato estável de entrega idempotente (transparência, PNCP, SICONFI/TCE).
 * Camada PURA: sem Spring/JPA. Implementação (outbox/broker/worker) à parte (RAZ-9).
 */
package br.contabil.plataforma.domain.entrega;
