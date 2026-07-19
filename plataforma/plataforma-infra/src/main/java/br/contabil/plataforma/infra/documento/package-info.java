/**
 * Adaptador de object store S3-compatível (ADR-0018): implementa a porta
 * {@link br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos} sobre
 * AWS S3 / MinIO, cifrando em repouso (SSE-KMS ou SSE-S3). Binding condicional em
 * {@code ObjectStoreConfiguration}; a ponte para os seams de assinatura vive em
 * {@code br.contabil.plataforma.infra.assinatura.ObjectStoreSeamsAssinaturaConfiguration}.
 */
package br.contabil.plataforma.infra.documento;
