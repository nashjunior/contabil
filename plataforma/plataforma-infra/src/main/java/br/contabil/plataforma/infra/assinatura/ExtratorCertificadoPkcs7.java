package br.contabil.plataforma.infra.assinatura;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Extrai o certificado do signatário embutido num pacote PKCS#7/CMS
 * {@code SignedData} — é assim que {@link ProvedorAssinaturaGovBr#assinarPkcs7}
 * devolve o certificado, sem precisar de uma chamada adicional à API.
 *
 * <p>Usa {@link CertificateFactory#generateCertificates} (JCE padrão), que sabe
 * ler a lista de certificados de um {@code SignedData} PKCS#7 diretamente.
 * Implementa {@code Function} (em vez de método estático) para ser um
 * colaborador injetável/substituível em teste, como os demais seams de
 * {@link ServicoAssinaturaGovBrAvancada}.
 */
final class ExtratorCertificadoPkcs7 implements Function<byte[], X509Certificate> {

    @Override
    public X509Certificate apply(byte[] pkcs7) {
        try {
            CertificateFactory fabrica = CertificateFactory.getInstance("X.509");
            Collection<? extends java.security.cert.Certificate> certificados =
                    fabrica.generateCertificates(new ByteArrayInputStream(pkcs7));

            // Um SignedData pode embutir a cadeia inteira; o certificado-folha do
            // signatário é o que NÃO é autoridade certificadora de nenhum outro
            // certificado do próprio pacote ("não aparece como issuer de nenhum
            // outro cert da lista").
            return certificados.stream()
                    .map(X509Certificate.class::cast)
                    .filter(candidato -> certificados.stream()
                            .map(X509Certificate.class::cast)
                            .noneMatch(outro -> outro != candidato
                                    && outro.getIssuerX500Principal().equals(candidato.getSubjectX500Principal())))
                    .max(Comparator.comparingLong(candidato -> candidato.getNotBefore().getTime()))
                    .orElseThrow(() -> new CertificateException("PKCS#7 sem certificado de signatário embutido"));
        } catch (CertificateException e) {
            throw new CertificadoPkcs7InvalidoException(e.getMessage(), e);
        }
    }

    /** PKCS#7 devolvido pelo provedor não pôde ser lido ou não tinha certificado embutido. */
    static final class CertificadoPkcs7InvalidoException extends RuntimeException {
        CertificadoPkcs7InvalidoException(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }
}
