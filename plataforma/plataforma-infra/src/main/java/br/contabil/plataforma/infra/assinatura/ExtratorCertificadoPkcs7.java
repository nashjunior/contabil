package br.contabil.plataforma.infra.assinatura;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Comparator;

/**
 * Extrai o certificado do signatário embutido num pacote PKCS#7/CMS
 * {@code SignedData} — é assim que {@link ProvedorAssinaturaGovBr#assinarPkcs7}
 * devolve o certificado, sem precisar de uma chamada adicional à API.
 *
 * <p>Usa {@link CertificateFactory#generateCertificates} (JCE padrão), que sabe
 * ler a lista de certificados de um {@code SignedData} PKCS#7 diretamente.
 */
final class ExtratorCertificadoPkcs7 {

    private ExtratorCertificadoPkcs7() {}

    /**
     * @throws CertificateException pacote PKCS#7 inválido ou sem nenhum certificado embutido
     */
    static X509Certificate certificadoDoSignatario(byte[] pkcs7) throws CertificateException {
        CertificateFactory fabrica = CertificateFactory.getInstance("X.509");
        Collection<? extends java.security.cert.Certificate> certificados =
                fabrica.generateCertificates(new ByteArrayInputStream(pkcs7));

        // Um SignedData pode embutir a cadeia inteira; o certificado-folha do
        // signatário é o que NÃO é autoridade certificadora de nenhum outro
        // certificado do próprio pacote — heurística padrão: maior data de
        // validade "notBefore" costuma ser a folha, mas o critério robusto é
        // "não aparece como issuer de nenhum outro cert da lista".
        return certificados.stream()
                .map(X509Certificate.class::cast)
                .filter(candidato -> certificados.stream()
                        .map(X509Certificate.class::cast)
                        .noneMatch(outro -> outro != candidato
                                && outro.getIssuerX500Principal().equals(candidato.getSubjectX500Principal())))
                .max(Comparator.comparing(X509Certificate::getNotBefore))
                .orElseThrow(() -> new CertificateException("PKCS#7 sem certificado de signatário embutido"));
    }
}
