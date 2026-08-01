package br.contabil.sessao;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import br.contabil.sessao.SessaoDevIdpController.SessaoDevIdpRequest;
import br.contabil.sessao.SessaoDevIdpController.SessaoDevIdpResponse;

class SessaoDevIdpControllerTest {

    private static final URI ISSUER = URI.create("https://e2e.siafic.local/idp");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);

    private final SessaoDevIdpController controller = new SessaoDevIdpController(assinadorDeTeste());

    @Test
    void emitirTokenDevolveBearerTokenComCpfSaneadoEEnteIdValido() {
        UUID enteId = UUID.randomUUID();

        ResponseEntity<?> resposta = controller.emitirToken(
                new SessaoDevIdpRequest("123.456.789-01", enteId.toString(), "LANCADOR"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        SessaoDevIdpResponse corpo = (SessaoDevIdpResponse) resposta.getBody();
        assertThat(corpo).isNotNull();
        assertThat(corpo.bearerToken().split("\\.")).hasSize(3);
    }

    @Test
    void emitirTokenRecusaCpfComMenosDe11Digitos() {
        ResponseEntity<?> resposta =
                controller.emitirToken(new SessaoDevIdpRequest("123", UUID.randomUUID().toString(), "LANCADOR"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).isEqualTo(Map.of("erro", "cpf_invalido"));
    }

    @Test
    void emitirTokenRecusaEnteIdQueNaoEUuid() {
        ResponseEntity<?> resposta =
                controller.emitirToken(new SessaoDevIdpRequest("12345678901", "nao-e-um-uuid", "LANCADOR"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).isEqualTo(Map.of("erro", "ente_id_invalido"));
    }

    @Test
    void emitirTokenRecusaEnteIdAusente() {
        ResponseEntity<?> resposta = controller.emitirToken(new SessaoDevIdpRequest("12345678901", null, "LANCADOR"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).isEqualTo(Map.of("erro", "ente_id_invalido"));
    }

    private static AssinadorJwtDevIdp assinadorDeTeste() {
        try {
            KeyPairGenerator geradorDeChaves = KeyPairGenerator.getInstance("RSA");
            geradorDeChaves.initialize(2048);
            KeyPair par = geradorDeChaves.generateKeyPair();
            SessaoDevIdpProperties properties = new SessaoDevIdpProperties(
                    true,
                    ISSUER,
                    "siafic-e2e",
                    pem(par.getPublic().getEncoded(), "PUBLIC"),
                    pem(par.getPrivate().getEncoded(), "PRIVATE"));
            return new AssinadorJwtDevIdp(JSON, CLOCK, properties);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pem(byte[] der, String tipo) {
        return "-----BEGIN " + tipo + " KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + tipo + " KEY-----";
    }
}
