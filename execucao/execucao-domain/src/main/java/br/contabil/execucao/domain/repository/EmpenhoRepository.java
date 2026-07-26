package br.contabil.execucao.domain.repository;

import java.net.URI;
import java.util.Optional;

import br.contabil.execucao.domain.DocumentoAssinadoEmpenho;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.plataforma.domain.TenantId;

/** Port de persistência de empenhos. */
public interface EmpenhoRepository {

    void inserir(Empenho empenho);

    Optional<Empenho> buscarPorId(TenantId enteId, EmpenhoId id);

    /**
     * Transição condicional {@code REGISTRADO -> PENDENTE_ASSINATURA} (ADR-0027
     * (b)) — {@code WHERE status = 'registrado'}. Devolve {@code false} sem
     * efeito quando o empenho já saiu de {@code REGISTRADO} (reprocessamento
     * idempotente do worker, ADR-0011).
     */
    boolean marcarPendenteAssinatura(TenantId enteId, EmpenhoId id, URI documentoPendenteUri);

    /**
     * Transição condicional {@code PENDENTE_ASSINATURA|ASSINATURA_REJEITADA ->
     * ASSINADO} (ADR-0027 (d)), persistindo {@code documentoAssinado} com FK ao
     * empenho na mesma operação. Update condicional / lock otimista (R3):
     * devolve {@code false} sem efeito quando o empenho não está mais num
     * estado assinável — duplo-clique / retry vira no-op, nunca uma segunda
     * assinatura.
     */
    boolean marcarAssinado(TenantId enteId, EmpenhoId id, DocumentoAssinadoEmpenho documentoAssinado);

    /**
     * Transição condicional {@code PENDENTE_ASSINATURA -> ASSINATURA_REJEITADA}
     * (ADR-0027 R2) — {@code WHERE status = 'pendente_assinatura'}. Nunca toca o
     * razão: só o ciclo de vida do documento.
     */
    boolean marcarAssinaturaRejeitada(TenantId enteId, EmpenhoId id);
}
