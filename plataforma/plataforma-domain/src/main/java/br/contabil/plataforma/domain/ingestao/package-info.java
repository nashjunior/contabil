/**
 * Port <b>Ingestão de integração</b> (doc 11 §Contratos; barramento ePING; ADR-0011
 * idempotência) — contrato estável da fronteira de entrada dos estruturantes (verifica
 * origem/assinatura + deduplica). Camada PURA: sem Spring/JPA.
 */
package br.contabil.plataforma.domain.ingestao;
