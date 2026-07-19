package br.contabil.plataforma.infra.assinatura;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Implementação real da API de Assinatura Eletrônica gov.br — avançada
 * (staging: {@code https://assinatura-api.staging.iti.br}), operação
 * {@code assinarPKCS7} (Roteiro de Integração API Assinatura avançada gov.br,
 * https://manual-integracao-assinatura-eletronica.servicos.gov.br/).
 *
 * <p>{@code POST {baseUri}/externo/v2/assinarPKCS7}, corpo
 * {@code {"hashBase64": "<sha-256 em base64>"}}, header
 * {@code Authorization: Bearer <token>} — o token é obtido pelo fluxo OAuth2
 * interativo (authorization_code, escopo {@code sign} ou
 * {@code signature_session}) que este adapter NÃO implementa (ver
 * package-info): {@code tokenAcesso} apenas consome um token já obtido.
 */
public final class ProvedorAssinaturaGovBrHttp implements ProvedorAssinaturaGovBr {

    private static final String CAMINHO_ASSINAR_PKCS7 = "/externo/v2/assinarPKCS7";

    private final HttpClient httpClient;
    private final URI baseUri;
    private final Supplier<String> tokenAcesso;

    public ProvedorAssinaturaGovBrHttp(HttpClient httpClient, URI baseUri, Supplier<String> tokenAcesso) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.tokenAcesso = Objects.requireNonNull(tokenAcesso, "tokenAcesso");
    }

    @Override
    public byte[] assinarPkcs7(byte[] hashSha256) {
        Objects.requireNonNull(hashSha256, "hashSha256");
        String hashBase64 = Base64.getEncoder().encodeToString(hashSha256);
        String corpo = "{\"hashBase64\":\"" + hashBase64 + "\"}";

        HttpRequest requisicao = HttpRequest.newBuilder(baseUri.resolve(CAMINHO_ASSINAR_PKCS7))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + tokenAcesso.get())
                .POST(BodyPublishers.ofString(corpo))
                .build();

        HttpResponse<byte[]> resposta;
        try {
            resposta = httpClient.send(requisicao, BodyHandlers.ofByteArray());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Falha de rede ao chamar assinarPKCS7 (gov.br)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido ao chamar assinarPKCS7 (gov.br)", e);
        }

        if (resposta.statusCode() == 403) {
            throw new ContaGovBrNaoElegivelException(
                    "gov.br recusou a assinatura (403): conta sem nível suficiente (Prata/Ouro) "
                            + "ou CPF com situação que impede o uso — corpo: "
                            + new String(resposta.body(), java.nio.charset.StandardCharsets.UTF_8));
        }
        if (resposta.statusCode() != 200) {
            throw new IllegalStateException(
                    "assinarPKCS7 (gov.br) respondeu status inesperado " + resposta.statusCode());
        }
        return resposta.body();
    }
}
