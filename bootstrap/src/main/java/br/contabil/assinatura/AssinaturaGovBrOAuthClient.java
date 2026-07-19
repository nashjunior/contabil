package br.contabil.assinatura;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

final class AssinaturaGovBrOAuthClient implements ClienteTokenAssinaturaGovBr {

    private static final Duration EXPIRACAO_PADRAO = Duration.ofMinutes(5);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AssinaturaGovBrOAuthProperties properties;

    AssinaturaGovBrOAuthClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Clock clock,
            AssinaturaGovBrOAuthProperties properties) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public AssinaturaGovBrOAuthToken trocarCodigoPorToken(String code, String codeVerifier) {
        properties.exigirConfiguracaoCompleta();
        String corpo = form(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", properties.redirectUri().toString(),
                "client_id", properties.clientId(),
                "client_secret", properties.clientSecret(),
                "code_verifier", codeVerifier);
        HttpRequest requisicao = HttpRequest.newBuilder(properties.tokenUri())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(corpo))
                .build();

        HttpResponse<String> resposta;
        try {
            resposta = httpClient.send(requisicao, BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Falha de rede ao trocar code OAuth2 por token gov.br", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido ao trocar code OAuth2 por token gov.br", e);
        }

        if (resposta.statusCode() < 200 || resposta.statusCode() >= 300) {
            throw new IllegalStateException("Token endpoint gov.br respondeu status " + resposta.statusCode());
        }
        return extrairToken(resposta.body());
    }

    private AssinaturaGovBrOAuthToken extrairToken(String corpo) {
        try {
            JsonNode json = objectMapper.readTree(corpo);
            JsonNode accessToken = json.get("access_token");
            if (accessToken == null || accessToken.asText().isBlank()) {
                throw new IllegalStateException("Token endpoint gov.br nao retornou access_token");
            }
            long expiresIn = json.path("expires_in").asLong(EXPIRACAO_PADRAO.toSeconds());
            return new AssinaturaGovBrOAuthToken(accessToken.asText(), clock.instant().plusSeconds(expiresIn));
        } catch (IOException e) {
            throw new IllegalStateException("Token endpoint gov.br retornou JSON invalido", e);
        }
    }

    private static String form(String... pares) {
        if (pares.length % 2 != 0) {
            throw new IllegalArgumentException("pares chave/valor invalidos");
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pares.length; i += 2) {
            if (i > 0) {
                builder.append('&');
            }
            builder.append(urlEncode(pares[i])).append('=').append(urlEncode(pares[i + 1]));
        }
        return builder.toString();
    }

    private static String urlEncode(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
