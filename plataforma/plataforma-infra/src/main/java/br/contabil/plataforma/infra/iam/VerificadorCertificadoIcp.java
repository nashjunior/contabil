package br.contabil.plataforma.infra.iam;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.contabil.plataforma.domain.iam.ServicoIdentidade;

final class VerificadorCertificadoIcp {

    private static final Pattern CPF_NO_SUBJECT = Pattern.compile(
            "(?:SERIALNUMBER|OID\\.2\\.16\\.76\\.1\\.3\\.1|OID\\.2\\.5\\.4\\.5)=([^,]+)",
            Pattern.CASE_INSENSITIVE);
    private static final String PEM_INICIO_CERT = "-----BEGIN CERTIFICATE-----";
    private static final String PEM_FIM_CERT = "-----END CERTIFICATE-----";

    private final Clock clock;
    private final IamProperties.Icp properties;

    VerificadorCertificadoIcp(Clock clock, IamProperties.Icp properties) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    IdentidadeVerificada verificar(String cadeiaCertificadoPem) {
        List<X509Certificate> cadeia = certificados(cadeiaCertificadoPem);
        X509Certificate certificado = cadeia.getFirst();
        try {
            certificado.checkValidity(java.util.Date.from(Instant.now(clock)));
            validarFingerprintProvisionado(certificado);
            String cpf = extrairCpf(certificado);
            return new IdentidadeVerificada(cpf, null, null, true, certificado.getNotAfter().toInstant());
        } catch (ServicoIdentidade.NaoAutenticadoException e) {
            throw e;
        } catch (Exception e) {
            throw new ServicoIdentidade.NaoAutenticadoException("certificado ICP-Brasil invalido: " + e.getMessage());
        }
    }

    private List<X509Certificate> certificados(String pem) {
        try {
            String texto = Objects.requireNonNull(pem, "pem");
            List<X509Certificate> certificados = new ArrayList<>();
            int inicioBusca = 0;
            while (true) {
                int inicio = texto.indexOf(PEM_INICIO_CERT, inicioBusca);
                if (inicio < 0) {
                    break;
                }
                int fim = texto.indexOf(PEM_FIM_CERT, inicio);
                if (fim < 0) {
                    throw new ServicoIdentidade.NaoAutenticadoException("cadeia ICP-Brasil malformada");
                }
                String bloco = texto.substring(inicio + PEM_INICIO_CERT.length(), fim).replaceAll("\\s", "");
                byte[] der = Base64.getDecoder().decode(bloco);
                certificados.add((X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(der)));
                inicioBusca = fim + PEM_FIM_CERT.length();
            }
            if (certificados.isEmpty()) {
                throw new ServicoIdentidade.NaoAutenticadoException("cadeia ICP-Brasil vazia");
            }
            return List.copyOf(certificados);
        } catch (CertificateException e) {
            throw new ServicoIdentidade.NaoAutenticadoException("cadeia ICP-Brasil malformada");
        }
    }

    private void validarFingerprintProvisionado(X509Certificate certificado) throws Exception {
        String fingerprint = sha256(certificado.getEncoded());
        if (!properties.certificadosConfiaveisSha256().contains(fingerprint)) {
            throw new ServicoIdentidade.NaoAutenticadoException(
                    "certificado ICP-Brasil nao provisionado como confiavel");
        }
    }

    private String extrairCpf(X509Certificate certificado) {
        for (String subject : List.of(
                certificado.getSubjectX500Principal().getName(),
                certificado.getSubjectX500Principal().getName("RFC1779"))) {
            Matcher matcher = CPF_NO_SUBJECT.matcher(subject);
            if (matcher.find()) {
                return IamProperties.normalizarCpf(matcher.group(1));
            }
        }
        throw new ServicoIdentidade.NaoAutenticadoException("certificado ICP-Brasil sem CPF no titular");
    }

    private String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format(Locale.ROOT, "%02x", b));
        }
        return hex.toString();
    }
}
