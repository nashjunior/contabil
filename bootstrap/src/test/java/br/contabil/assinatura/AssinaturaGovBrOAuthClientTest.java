package br.contabil.assinatura;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssinaturaGovBrOAuthClientTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);

    private HttpServer servidor;
    private final AtomicReference<String> corpoRecebido = new AtomicReference<>();

    @BeforeEach
    void sobeServidorToken() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/oauth2.0/accessToken", exchange -> {
            corpoRecebido.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resposta = "{\"access_token\":\"token-http\",\"expires_in\":60}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });
        servidor.start();
    }

    @AfterEach
    void paraServidorToken() {
        servidor.stop(0);
    }

    @Test
    void trocaCodigoPorTokenNoEndpointConfiguradoComPkce() {
        AssinaturaGovBrOAuthProperties properties = new AssinaturaGovBrOAuthProperties(
                URI.create("https://cas.staging.iti.br/oauth2.0/authorize"),
                URI.create("http://localhost:" + servidor.getAddress().getPort() + "/oauth2.0/accessToken"),
                "cliente-siafic",
                "segredo",
                URI.create("http://localhost:8080/assinatura/oauth/callback"),
                URI.create("http://localhost:5173/execucao/assinatura/retorno"),
                List.of("sign", "signature_session"),
                Duration.ofMinutes(10));
        var client = new AssinaturaGovBrOAuthClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                CLOCK,
                properties,
                Duration.ofSeconds(2),
                CircuitBreaker.ofDefaults("assinatura-govbr-teste"));

        AssinaturaGovBrOAuthToken token = client.trocarCodigoPorToken("codigo", "verifier");

        assertThat(token.accessToken()).isEqualTo("token-http");
        assertThat(token.expiraEm()).isEqualTo(Instant.parse("2026-07-19T12:01:00Z"));
        Map<String, String> form = formRecebido();
        assertThat(form)
                .containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "codigo")
                .containsEntry("redirect_uri", "http://localhost:8080/assinatura/oauth/callback")
                .containsEntry("client_id", "cliente-siafic")
                .containsEntry("client_secret", "segredo")
                .containsEntry("code_verifier", "verifier");
    }

    private Map<String, String> formRecebido() {
        return Arrays.stream(corpoRecebido.get().split("&"))
                .map(par -> par.split("=", 2))
                .collect(Collectors.toMap(
                        partes -> decode(partes[0]),
                        partes -> partes.length == 1 ? "" : decode(partes[1])));
    }

    private static String decode(String valor) {
        return URLDecoder.decode(valor, StandardCharsets.UTF_8);
    }
}
