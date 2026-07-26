package br.contabil.execucao.infra.documento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port de infra do outbox dedicado de {@code execucao_empenho_pendente_documento}
 * (ADR-0027 (a)/(b)) — mesma forma de {@code OutboxMensagemRepository}
 * (plataforma-infra/entrega), mas tabela própria: o consumidor aqui processa o
 * evento localmente (gera PDF, grava no object store, transiciona o Empenho),
 * não despacha a um broker externo.
 */
interface EmpenhoDocumentoOutboxRepository {

    List<MensagemEmpenhoDocumentoOutbox> reclamar(int limite, Instant bloqueadoAte);

    void confirmar(UUID id);

    void registrarRetentativa(UUID id, Instant proximaTentativa, String erro);

    void enviarParaDlq(UUID id, String erro);
}
