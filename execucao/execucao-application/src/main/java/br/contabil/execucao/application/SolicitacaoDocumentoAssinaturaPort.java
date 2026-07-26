package br.contabil.execucao.application;

import br.contabil.execucao.domain.Empenho;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Porta de solicitação assíncrona do documento (nota de empenho) a assinar
 * (ADR-0027 (a), mesma forma de {@link PublicacaoTransparenciaExecucaoPort}).
 *
 * <p>A implementação deve enfileirar no outbox transacional (ADR-0004) o evento
 * {@code execucao_empenho_pendente_documento}, na MESMA transação do fato — nunca
 * gerar o PDF/A ou tocar o object store de forma síncrona aqui.
 */
public interface SolicitacaoDocumentoAssinaturaPort {

    void solicitar(Empenho empenho, Sessao sessao);
}
