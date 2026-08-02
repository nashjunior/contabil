package br.contabil.sessao;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config do IdP de dev local (RAZ-228/ADR-0052 item 1) — reaproveita as MESMAS env vars
 * {@code GOVBR_IAM_ISSUER}/{@code GOVBR_IAM_AUDIENCE}/{@code GOVBR_IAM_PUBLIC_KEY_PEM} que
 * {@code IamProperties.GovBr} (plataforma-infra) já usa para VERIFICAR — o mesmo par de
 * chaves de dev que o e2e já usa ({@code frontend/e2e/support/jwt.ts}). Só a chave PRIVADA
 * é exclusiva daqui ({@code SIAFIC_DEV_IDP_PRIVATE_KEY_PEM}), porque só este endpoint
 * ASSINA — nunca vive no bundle do navegador (ADR-0052 "Alternativas consideradas" item 1).
 */
@ConfigurationProperties(prefix = "siafic.dev-idp")
record SessaoDevIdpProperties(boolean enabled, URI issuer, String audience, String publicKeyPem, String privateKeyPem) {

    SessaoDevIdpProperties {
        audience = audience == null ? "" : audience.trim();
        publicKeyPem = publicKeyPem == null ? "" : publicKeyPem.trim();
        privateKeyPem = privateKeyPem == null ? "" : privateKeyPem.trim();
    }

    /** Fail-closed explícito: exige o par de chaves de dev completo antes de assinar qualquer coisa. */
    void exigirConfiguracaoCompleta() {
        if (issuer == null || !issuer.isAbsolute()) {
            throw new IllegalStateException(
                    "siafic.dev-idp habilitado exige siafic.iam.govbr.issuer (GOVBR_IAM_ISSUER) configurado");
        }
        if (audience.isBlank()) {
            throw new IllegalStateException(
                    "siafic.dev-idp habilitado exige siafic.iam.govbr.audience (GOVBR_IAM_AUDIENCE) configurado");
        }
        if (publicKeyPem.isBlank()) {
            throw new IllegalStateException(
                    "siafic.dev-idp habilitado exige siafic.iam.govbr.public-key-pem (GOVBR_IAM_PUBLIC_KEY_PEM) configurado");
        }
        if (privateKeyPem.isBlank()) {
            throw new IllegalStateException(
                    "siafic.dev-idp habilitado exige siafic.dev-idp.private-key-pem (SIAFIC_DEV_IDP_PRIVATE_KEY_PEM) configurado");
        }
    }
}
