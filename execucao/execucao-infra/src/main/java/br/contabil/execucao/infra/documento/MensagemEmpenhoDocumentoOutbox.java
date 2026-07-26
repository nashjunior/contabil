package br.contabil.execucao.infra.documento;

import java.util.UUID;

import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.plataforma.domain.TenantId;

/** Linha reclamada de {@code execucao_empenho_documento_outbox}. */
record MensagemEmpenhoDocumentoOutbox(UUID id, TenantId enteId, EmpenhoId empenhoId, int tentativas) {}
