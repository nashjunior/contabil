/**
 * Contrato de armazenamento de documentos binários (ADR-0009/0018): a porta
 * {@link br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos},
 * dirigida por {@link java.net.URI}. O conteúdo vive em object store/GED cifrado;
 * a base guarda apenas a referência (nunca BLOB). Capacidade de plataforma
 * reutilizável; escopo por tenant via namespacing da URI (ADR-0015/0018).
 */
package br.contabil.plataforma.domain.documento;
