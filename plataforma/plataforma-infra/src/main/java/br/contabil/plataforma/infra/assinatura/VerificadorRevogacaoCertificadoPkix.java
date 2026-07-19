package br.contabil.plataforma.infra.assinatura;

import java.security.GeneralSecurityException;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Checagem de revogação via {@code CertPathValidator("PKIX")} do próprio JDK
 * com {@link PKIXRevocationChecker} — tenta OCSP primeiro e cai para CRL
 * (comportamento padrão do checker, sem {@link Option#PREFER_CRLS}), com
 * {@link Option#SOFT_FAIL} desligado: falha ao checar é tratada como
 * "não validado", nunca como "válido por omissão" (deny-by-default, igual à
 * RLS do razão).
 *
 * <p>As âncoras de confiança (cadeia raiz/intermediária ICP-Brasil) são
 * injetadas — este adapter não empacota certificados de CA (obtenção e
 * rotação do bundle oficial da ICP-Brasil é decisão de implantação/config,
 * não de código-fonte).
 */
public final class VerificadorRevogacaoCertificadoPkix implements VerificadorRevogacaoCertificado {

    private final Set<TrustAnchor> ancorasConfianca;

    public VerificadorRevogacaoCertificadoPkix(Set<TrustAnchor> ancorasConfianca) {
        this.ancorasConfianca = Set.copyOf(Objects.requireNonNull(ancorasConfianca, "ancorasConfianca"));
        if (this.ancorasConfianca.isEmpty()) {
            throw new IllegalArgumentException("ancorasConfianca não pode ser vazio");
        }
    }

    @Override
    public ResultadoRevogacao verificar(X509Certificate certificado) {
        Objects.requireNonNull(certificado, "certificado");
        try {
            CertPathValidator validador = CertPathValidator.getInstance("PKIX");
            PKIXRevocationChecker checker = (PKIXRevocationChecker) validador.getRevocationChecker();
            checker.setOptions(EnumSet.noneOf(PKIXRevocationChecker.Option.class)); // sem SOFT_FAIL

            PKIXParameters parametros = new PKIXParameters(ancorasConfianca);
            parametros.addCertPathChecker(checker);

            var caminho = CertificateFactory.getInstance("X.509").generateCertPath(List.of(certificado));
            validador.validate(caminho, parametros);

            return new ResultadoRevogacao(false, "certificado válido (OCSP/CRL sem indicação de revogação)");
        } catch (CertPathValidatorException e) {
            // Deny-by-default: qualquer falha na validação do caminho — revogado
            // de fato ou status indeterminado (OCSP/CRL indisponível) — é tratada
            // como não seguro para assinar, nunca como "válido por omissão".
            return new ResultadoRevogacao(true, "checagem de revogação falhou: " + e.getReason() + " — " + e.getMessage());
        } catch (GeneralSecurityException e) {
            return new ResultadoRevogacao(true, "não foi possível checar revogação: " + e.getMessage());
        }
    }
}
