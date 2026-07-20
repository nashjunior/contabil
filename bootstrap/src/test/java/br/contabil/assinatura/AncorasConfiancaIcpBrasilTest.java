package br.contabil.assinatura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.TrustAnchor;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AncorasConfiancaIcpBrasilTest {

    @TempDir
    Path tempDir;

    @Test
    void carrega_bundle_pem_com_um_certificado_autoassinado() throws Exception {
        Path bundlePem = tempDir.resolve("bundle.pem");
        gerarCertificadoAutoassinadoPem(bundlePem);

        Set<TrustAnchor> ancoras = AncorasConfiancaIcpBrasil.carregar(bundlePem);

        assertThat(ancoras).hasSize(1);
        assertThat(ancoras.iterator().next().getTrustedCert().getSubjectX500Principal().getName())
                .contains("CN=ancora-teste");
    }

    @Test
    void rejeita_caminho_inexistente() {
        Path inexistente = tempDir.resolve("nao-existe.pem");

        assertThatThrownBy(() -> AncorasConfiancaIcpBrasil.carregar(inexistente))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao encontrado");
    }

    @Test
    void rejeita_arquivo_sem_certificados() throws IOException {
        Path vazio = tempDir.resolve("vazio.pem");
        Files.writeString(vazio, "não é um certificado");

        assertThatThrownBy(() -> AncorasConfiancaIcpBrasil.carregar(vazio))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Gera um par de chaves + certificado autoassinado via {@code keytool} (JDK), exportado em PEM. */
    private static void gerarCertificadoAutoassinadoPem(Path destinoPem) throws IOException, InterruptedException {
        Path keystore = destinoPem.resolveSibling("keystore-teste.p12");
        executar(
                "keytool", "-genkeypair",
                "-alias", "ancora-teste",
                "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1",
                "-dname", "CN=ancora-teste",
                "-keystore", keystore.toString(),
                "-storetype", "PKCS12",
                "-storepass", "changeit");
        executar(
                "keytool", "-exportcert",
                "-alias", "ancora-teste",
                "-keystore", keystore.toString(),
                "-storepass", "changeit",
                "-rfc",
                "-file", destinoPem.toString());
    }

    private static void executar(String... comando) throws IOException, InterruptedException {
        Process processo = new ProcessBuilder(comando).redirectErrorStream(true).start();
        String saida = new String(processo.getInputStream().readAllBytes());
        int status = processo.waitFor();
        if (status != 0) {
            throw new IllegalStateException("comando falhou (" + status + "): " + saida);
        }
    }
}
