package br.contabil.plataforma.domain.ingestao;

import java.util.Objects;

import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.ErroContrato;
import br.contabil.plataforma.domain.TenantId;

/**
 * Contrato do serviço de <b>Ingestão de integração</b> (doc 11 §Contratos; barramento
 * ePING; ADR-0011 idempotência ponta a ponta).
 *
 * <p>Fronteira de entrada dos estruturantes externos (licitações, patrimônio, folha,
 * tributos). Toda mensagem tem sua <b>origem/assinatura verificada</b> (mTLS/HMAC/allowlist)
 * e é <b>deduplicada</b> por {@link ChaveIdempotencia} (inbox). Reentrega da mesma chave
 * é {@link ResultadoIngestao#DUPLICADO} — resultado normal, não erro.
 */
public interface ServicoIngestao {

    /**
     * Recebe uma mensagem de um estruturante, após verificar origem/assinatura e deduplicar.
     *
     * @throws OrigemNaoConfiavelException origem fora da allowlist / não confiável
     * @throws AssinaturaInvalidaException assinatura (mTLS/HMAC) inválida
     */
    ResultadoIngestao receber(MensagemIngestao mensagem, OrigemIntegracao origem);

    // ---- Value objects do contrato --------------------------------------------

    /** Mensagem recebida: tipo, conteúdo, chave de idempotência e a assinatura a verificar. */
    record MensagemIngestao(String tipo, String conteudo, ChaveIdempotencia chave, String assinatura) {
        public MensagemIngestao {
            Objects.requireNonNull(tipo, "tipo da mensagem");
            Objects.requireNonNull(conteudo, "conteúdo da mensagem");
            Objects.requireNonNull(chave, "chave de idempotência");
            Objects.requireNonNull(assinatura, "assinatura da mensagem");
        }
    }

    /** Origem da integração: sistema estruturante emissor + ente ao qual pertence. */
    record OrigemIntegracao(String sistema, TenantId ente) {
        public OrigemIntegracao {
            Objects.requireNonNull(sistema, "sistema de origem");
            Objects.requireNonNull(ente, "ente da origem");
        }
    }

    /** Resultado da ingestão: aceita (processada) ou duplicada (idempotente, efeito único). */
    enum ResultadoIngestao {
        ACEITA,
        DUPLICADO
    }

    // ---- Erros do contrato (doc 11 §Contratos) --------------------------------

    /** Erro {@code origem_nao_confiavel}: emissor fora da allowlist / não autenticado. */
    final class OrigemNaoConfiavelException extends RuntimeException implements ErroContrato {
        public OrigemNaoConfiavelException(String mensagem) {
            super(mensagem);
        }

        @Override
        public String codigo() {
            return "origem_nao_confiavel";
        }
    }

    /** Erro {@code assinatura_invalida}: assinatura (mTLS/HMAC) da mensagem não confere. */
    final class AssinaturaInvalidaException extends RuntimeException implements ErroContrato {
        public AssinaturaInvalidaException(String mensagem) {
            super(mensagem);
        }

        @Override
        public String codigo() {
            return "assinatura_invalida";
        }
    }
}
