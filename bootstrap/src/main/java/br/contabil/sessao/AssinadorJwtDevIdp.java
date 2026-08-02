package br.contabil.sessao;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Assina JWTs RS256 no MESMO contrato de claims que {@code VerificadorJwtGovBr}
 * (plataforma-infra) exige — {@code cpf}/{@code ente_id}/{@code nivel_conta}, mesmos
 * nomes default de {@code IamProperties.GovBr} — reaproveitando a mecânica já provada
 * por {@code AssinadorJwtE2e} (frontend/e2e/support/jwt.ts), agora alcançável por HTTP
 * (RAZ-228/ADR-0052 item 1).
 *
 * <p>{@code papel} do request do controller NÃO vira claim: RBAC é resolvido só por
 * {@code siafic.iam.concessoes} server-side (deny-by-default), nunca por um claim
 * auto-declarado — o dev precisa ter a concessão correspondente já provisionada para o
 * CPF/ente escolhidos, exatamente como valeria em produção.
 *
 * <p>Nível de garantia fixo em {@code OURO} (MFA forte): o dev-IdP não modela níveis de
 * garantia gov.br diferentes, só precisa liberar as telas que exigem MFA.
 *
 * <p>Verifica no construtor que a chave privada configurada realmente PAREIA com
 * {@code siafic.iam.govbr.public-key-pem} (assina um nonce e reverifica com a chave
 * pública) — fail-fast: se o par não bate, o processo recusa subir em vez de expor um
 * endpoint que só emitiria bearers que {@code VerificadorJwtGovBr} rejeitaria de
 * qualquer forma (mesmo racional do ADR-0052 §Consequências).
 */
final class AssinadorJwtDevIdp {

    private static final Duration TTL_TOKEN = Duration.ofHours(1);
    private static final String NIVEL_CONTA_DEV = "OURO";

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SessaoDevIdpProperties properties;
    private final PrivateKey privateKey;

    AssinadorJwtDevIdp(ObjectMapper objectMapper, Clock clock, SessaoDevIdpProperties properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.properties = Objects.requireNonNull(properties, "properties");
        properties.exigirConfiguracaoCompleta();
        this.privateKey = chavePrivada(properties.privateKeyPem());
        exigirParDeChavesValido();
    }

    String assinar(String cpf, UUID enteId) {
        Instant agora = Instant.now(clock);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", properties.issuer().toString());
        claims.put("aud", properties.audience());
        claims.put("exp", agora.plus(TTL_TOKEN).getEpochSecond());
        claims.put("cpf", cpf);
        claims.put("ente_id", enteId.toString());
        claims.put("nivel_conta", NIVEL_CONTA_DEV);

        String signingInput = base64Url(escrever(header)) + "." + base64Url(escrever(claims));
        return signingInput + "." + base64Url(assinarBytes(signingInput, privateKey));
    }

    private void exigirParDeChavesValido() {
        PublicKey publicKey = chavePublica(properties.publicKeyPem());
        String nonce = "dev-idp-self-check-" + UUID.randomUUID();
        byte[] assinatura = assinarBytes(nonce, privateKey);
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(nonce.getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(assinatura)) {
                throw new IllegalStateException(
                        "siafic.dev-idp.private-key-pem nao pareia com siafic.iam.govbr.public-key-pem — "
                                + "gere um par de chaves RSA novo para o dev-IdP (mesma mecânica de "
                                + "frontend/e2e/support/jwt.ts) e configure as duas metades");
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("falha ao validar o par de chaves do dev-IdP", e);
        }
    }

    private byte[] escrever(Map<String, Object> valor) {
        try {
            return objectMapper.writeValueAsBytes(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("falha ao serializar claims do dev-IdP", e);
        }
    }

    private static byte[] assinarBytes(String signingInput, PrivateKey chave) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(chave);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("falha ao assinar JWT do dev-IdP", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static PrivateKey chavePrivada(String pem) {
        try {
            String normalizado = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(normalizado);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("siafic.dev-idp.private-key-pem invalida (esperado PKCS8 PEM)", e);
        }
    }

    private static PublicKey chavePublica(String pem) {
        try {
            String normalizado = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(normalizado);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("siafic.iam.govbr.public-key-pem invalida", e);
        }
    }
}
