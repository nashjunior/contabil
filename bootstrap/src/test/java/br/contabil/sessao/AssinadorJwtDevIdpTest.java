package br.contabil.sessao;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssinadorJwtDevIdpTest {

    private static final URI ISSUER = URI.create("https://e2e.siafic.local/idp");
    private static final String AUDIENCE = "siafic-e2e";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void assinarProduzJwtRs256VerificavelComAChavePublicaPareada() throws Exception {
        KeyPair par = gerarParDeChaves();
        AssinadorJwtDevIdp assinador = new AssinadorJwtDevIdp(JSON, CLOCK, propriedades(par));

        UUID enteId = UUID.randomUUID();
        String jwt = assinador.assinar("12345678901", enteId);

        String[] partes = jwt.split("\\.");
        assertThat(partes).hasSize(3);

        Map<String, Object> claims =
                JSON.readValue(Base64.getUrlDecoder().decode(partes[1]), new TypeReference<>() {});
        assertThat(claims).containsEntry("iss", ISSUER.toString());
        assertThat(claims).containsEntry("aud", AUDIENCE);
        assertThat(claims).containsEntry("cpf", "12345678901");
        assertThat(claims).containsEntry("ente_id", enteId.toString());
        assertThat(claims).containsEntry("nivel_conta", "OURO");
        assertThat(((Number) claims.get("exp")).longValue()).isGreaterThan(CLOCK.instant().getEpochSecond());

        Signature verificador = Signature.getInstance("SHA256withRSA");
        verificador.initVerify(par.getPublic());
        verificador.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verificador.verify(Base64.getUrlDecoder().decode(partes[2]))).isTrue();
    }

    @Test
    void construtorRecusaQuandoChavePrivadaNaoPareiaComAChavePublicaConfigurada() throws Exception {
        KeyPair parDoSigner = gerarParDeChaves();
        KeyPair parDaVerificacao = gerarParDeChaves();
        SessaoDevIdpProperties properties = new SessaoDevIdpProperties(
                true, ISSUER, AUDIENCE, pem(parDaVerificacao.getPublic().getEncoded(), "PUBLIC"),
                pem(parDoSigner.getPrivate().getEncoded(), "PRIVATE"));

        assertThatThrownBy(() -> new AssinadorJwtDevIdp(JSON, CLOCK, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao pareia");
    }

    @Test
    void construtorRecusaConfiguracaoIncompleta() {
        SessaoDevIdpProperties properties = new SessaoDevIdpProperties(true, ISSUER, AUDIENCE, "", "");

        assertThatThrownBy(() -> new AssinadorJwtDevIdp(JSON, CLOCK, properties))
                .isInstanceOf(IllegalStateException.class);
    }

    private static SessaoDevIdpProperties propriedades(KeyPair par) {
        return new SessaoDevIdpProperties(
                true,
                ISSUER,
                AUDIENCE,
                pem(par.getPublic().getEncoded(), "PUBLIC"),
                pem(par.getPrivate().getEncoded(), "PRIVATE"));
    }

    private static KeyPair gerarParDeChaves() throws Exception {
        KeyPairGenerator geradorDeChaves = KeyPairGenerator.getInstance("RSA");
        geradorDeChaves.initialize(2048);
        return geradorDeChaves.generateKeyPair();
    }

    private static String pem(byte[] der, String tipo) {
        return "-----BEGIN " + tipo + " KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + tipo + " KEY-----";
    }
}
