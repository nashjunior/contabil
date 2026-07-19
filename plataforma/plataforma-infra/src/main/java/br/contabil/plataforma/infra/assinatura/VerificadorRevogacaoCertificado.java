package br.contabil.plataforma.infra.assinatura;

import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * Checagem do status de revogação (OCSP e/ou LCR/CRL) de um certificado no ato
 * da assinatura — item 6 de {@code transversais/01-assinatura-eletronica.md}
 * [F0]. Independente de provedor: qualquer certificado X.509 (gov.br ou,
 * futuramente, ICP-Brasil qualificada) passa pelo mesmo contrato.
 */
public interface VerificadorRevogacaoCertificado {

    ResultadoRevogacao verificar(X509Certificate certificado);

    /**
     * {@code revogado=true} cobre tanto revogação confirmada quanto status
     * indeterminado (OCSP/CRL indisponível, cadeia não validada) — deny-by-default,
     * nunca "válido por omissão". {@code detalhe} nunca é nulo — sempre há uma
     * explicação para registrar na trilha.
     */
    record ResultadoRevogacao(boolean revogado, String detalhe) {
        public ResultadoRevogacao {
            Objects.requireNonNull(detalhe, "detalhe do resultado de revogação");
        }
    }
}
