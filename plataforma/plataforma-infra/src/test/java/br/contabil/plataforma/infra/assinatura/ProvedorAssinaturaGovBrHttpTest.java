package br.contabil.plataforma.infra.assinatura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sobe um {@link HttpServer} local (JDK, sem dependência extra) para provar que
 * {@link ProvedorAssinaturaGovBrHttp} monta a requisição {@code assinarPKCS7}
 * exatamente como o manual oficial descreve — não é possível testar contra o
 * ambiente real do gov.br aqui (exige credencial de cliente OAuth2 e o fluxo
 * interativo do signatário).
 */
class ProvedorAssinaturaGovBrHttpTest {

    private HttpServer servidor;
    private ProvedorAssinaturaGovBrHttp provedor;
    private final AtomicReference<String> corpoRecebido = new AtomicReference<>();
    private final AtomicReference<String> headerAutorizacaoRecebido = new AtomicReference<>();
    private volatile int statusParaResponder = 200;
    private volatile byte[] corpoParaResponder = "pkcs7-binario-fake".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void sobeServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/externo/v2/assinarPKCS7", exchange -> {
            headerAutorizacaoRecebido.set(exchange.getRequestHeaders().getFirst("Authorization"));
            corpoRecebido.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(statusParaResponder, corpoParaResponder.length);
            exchange.getResponseBody().write(corpoParaResponder);
            exchange.close();
        });
        servidor.start();

        provedor = new ProvedorAssinaturaGovBrHttp(
                HttpClient.newHttpClient(),
                URI.create("http://localhost:" + servidor.getAddress().getPort()),
                () -> "token-de-teste");
    }

    @AfterEach
    void paraServidor() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("monta POST com hashBase64 no corpo, Authorization: Bearer, e devolve o corpo da resposta")
    void assinarPkcs7MontaRequisicaoCorreta() {
        byte[] hash = "hash-sha256-fake".getBytes(StandardCharsets.UTF_8);

        byte[] resposta = provedor.assinarPkcs7(hash);

        assertThat(resposta).isEqualTo(corpoParaResponder);
        assertThat(headerAutorizacaoRecebido.get()).isEqualTo("Bearer token-de-teste");
        assertThat(corpoRecebido.get())
                .isEqualTo("{\"hashBase64\":\"" + java.util.Base64.getEncoder().encodeToString(hash) + "\"}");
    }

    @Test
    @DisplayName("403 (conta Bronze/CPF sem situação) vira ContaGovBrNaoElegivelException")
    void status403VidaContaNaoElegivel() {
        statusParaResponder = 403;
        corpoParaResponder = "Cidadão não possui a identidade (Prata ou Ouro)".getBytes(StandardCharsets.UTF_8);

        assertThatExceptionOfType(ProvedorAssinaturaGovBr.ContaGovBrNaoElegivelException.class)
                .isThrownBy(() -> provedor.assinarPkcs7("hash".getBytes(StandardCharsets.UTF_8)))
                .withMessageContaining("Prata ou Ouro");
    }

    @Test
    @DisplayName("status inesperado vira IllegalStateException")
    void statusInesperadoLancaExcecao() {
        statusParaResponder = 500;

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> provedor.assinarPkcs7("hash".getBytes(StandardCharsets.UTF_8)));
    }
}
