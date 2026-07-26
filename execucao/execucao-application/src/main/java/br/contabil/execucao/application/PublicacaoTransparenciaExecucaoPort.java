package br.contabil.execucao.application;

import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.Pagamento;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Porta de publicação da execução na transparência ativa.
 *
 * <p>A implementação deve enfileirar no outbox transacional (RAZ-9/ADR-0004); nunca chamar
 * o portal/API pública de forma síncrona dentro do caso de uso.
 */
public interface PublicacaoTransparenciaExecucaoPort {

    void publicar(Empenho empenho, Sessao sessao);

    void publicar(Liquidacao liquidacao, Sessao sessao);

    void publicar(Pagamento pagamento, Sessao sessao);
}
