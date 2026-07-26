package br.contabil.plataforma.infra.assinatura;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Teste dedicado de {@link VerificadorRevogacaoCertificadoPkix} — RAZ-74 (achado do RAZ-68: a
 * classe não tinha nenhum teste próprio; o único teste que a tocava
 * ({@code ServicoAssinaturaGovBrAvancadaTest.rejeitaCertificadoRevogado}) usa um mock que força
 * {@code revogado=true} e nunca exercita OCSP/CRL de verdade).
 *
 * <p>Monta uma cadeia real (CA autoassinada + certificados-folha) via {@code openssl}, sobe uma
 * CRL (Lista de Certificados Revogados) real assinada pela CA e a serve por HTTP local — sem
 * nenhum mock do {@code CertPathValidator("PKIX")} nem do {@code PKIXRevocationChecker} do JDK:
 * exercita exatamente o mesmo caminho de código que roda em produção contra ICP-Brasil/gov.br,
 * só que com uma CA e uma CRL de teste em vez da cadeia oficial (RAZ-37: sem PII real — CPF/nome
 * fictícios, chave/CA descartáveis geradas a cada execução).
 *
 * <p>Cada certificado-folha usa um caminho de CRLDP (CRL Distribution Point) diferente mesmo
 * compartilhando o servidor HTTP: o {@code CertPathValidator} do JDK cacheia CRLs buscadas por
 * URI dentro do mesmo processo, e reusar a mesma URI para respostas diferentes entre os testes
 * fica sujeito a esse cache — descoberto empiricamente ao prototipar este teste.
 */
class VerificadorRevogacaoCertificadoPkixTest {

    @TempDir
    static Path dir;

    private static HttpServer servidorCrl;
    private static X509Certificate certificadoCa;
    private static X509Certificate certificadoValido;
    private static X509Certificate certificadoRevogado;
    private static X509Certificate certificadoPontoDistribuicaoIndisponivel;

    @BeforeAll
    static void montaCadeiaEServeCrlReal() throws Exception {
        Map<String, byte[]> respostasPorCaminho = new ConcurrentHashMap<>();
        servidorCrl = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidorCrl.createContext("/", exchange -> {
            byte[] corpo = respostasPorCaminho.get(exchange.getRequestURI().getPath());
            if (corpo == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/pkix-crl");
            exchange.sendResponseHeaders(200, corpo.length);
            exchange.getResponseBody().write(corpo);
            exchange.close();
        });
        servidorCrl.start();
        int porta = servidorCrl.getAddress().getPort();

        gerarAutoridadeCertificadoraDeTeste();
        certificadoCa = carregarCertificado(dir.resolve("ca.crt"));

        certificadoValido = gerarCertificadoFolha("valido", porta, "/valido.crl");
        certificadoRevogado = gerarCertificadoFolha("revogado", porta, "/revogado.crl");
        certificadoPontoDistribuicaoIndisponivel =
                gerarCertificadoFolha("indisponivel", portaLivreEFechada(), "/indisponivel.crl");

        configurarBaseDaAutoridadeCertificadora();

        // CRL "antes" — nenhuma revogação — servida no path do certificado válido.
        respostasPorCaminho.put("/valido.crl", gerarCrlReal("valido"));

        // Revoga só o certificado "revogado" e gera a CRL "depois" — servida no path dele.
        executar("openssl", "ca", "-config", "ca.cnf", "-revoke", "revogado.crt");
        respostasPorCaminho.put("/revogado.crl", gerarCrlReal("revogado"));
    }

    @AfterAll
    static void paraServidor() {
        servidorCrl.stop(0);
    }

    @Test
    @DisplayName("certificado não revogado: CRL real (CRLDP + servidor HTTP local) confirma válido, sem mock")
    void certificadoNaoRevogadoEhAceito() {
        var verificador = new VerificadorRevogacaoCertificadoPkix(Set.of(new TrustAnchor(certificadoCa, null)));

        var resultado = verificador.verificar(certificadoValido);

        assertThat(resultado.revogado()).isFalse();
        assertThat(resultado.detalhe()).contains("válido");
    }

    @Test
    @DisplayName("certificado revogado: CRL real assinada pela CA de teste é reconhecida — não é o mock que força revogado=true")
    void certificadoRevogadoEhBloqueado() {
        var verificador = new VerificadorRevogacaoCertificadoPkix(Set.of(new TrustAnchor(certificadoCa, null)));

        var resultado = verificador.verificar(certificadoRevogado);

        assertThat(resultado.revogado()).isTrue();
        assertThat(resultado.detalhe()).contains("REVOKED");
    }

    @Test
    @DisplayName("ponto de distribuição de CRL inalcançável: SOFT_FAIL desligado trata a falha como revogado (deny-by-default), nunca como válido por omissão")
    void falhaAoAlcancarCrlEhTratadaComoRevogado() {
        var verificador = new VerificadorRevogacaoCertificadoPkix(Set.of(new TrustAnchor(certificadoCa, null)));

        var resultado = verificador.verificar(certificadoPontoDistribuicaoIndisponivel);

        assertThat(resultado.revogado()).isTrue();
        assertThat(resultado.detalhe()).contains("checagem de revogação falhou");
    }

    private static void gerarAutoridadeCertificadoraDeTeste() throws IOException, InterruptedException {
        executar(
                "openssl", "req", "-x509", "-newkey", "rsa:2048",
                "-keyout", "ca.key", "-out", "ca.crt", "-days", "2", "-nodes",
                "-subj", "/CN=AC Teste RAZ-74",
                "-addext", "basicConstraints=critical,CA:true",
                "-addext", "keyUsage=critical,keyCertSign,cRLSign");
    }

    /** Gera um certificado-folha assinado pela CA de teste, com CRLDP apontando pro servidor HTTP local. */
    private static X509Certificate gerarCertificadoFolha(String nome, int porta, String caminhoCrl)
            throws IOException, InterruptedException {
        executar(
                "openssl", "req", "-newkey", "rsa:2048",
                "-keyout", nome + ".key", "-out", nome + ".csr", "-nodes",
                // RAZ-37: nome fictício, sem PII real.
                "-subj", "/CN=Signatario Teste " + nome);
        Files.writeString(
                dir.resolve(nome + ".ext"),
                """
                basicConstraints=CA:FALSE
                keyUsage=digitalSignature
                crlDistributionPoints=URI:http://127.0.0.1:%d%s
                """
                        .formatted(porta, caminhoCrl));
        executar(
                "openssl", "x509", "-req", "-in", nome + ".csr",
                "-CA", "ca.crt", "-CAkey", "ca.key", "-CAcreateserial",
                "-out", nome + ".crt", "-days", "2", "-extfile", nome + ".ext");
        return carregarCertificado(dir.resolve(nome + ".crt"));
    }

    /** CA mínima de linha de comando do openssl ({@code openssl ca}) — só o necessário pra emitir uma CRL real. */
    private static void configurarBaseDaAutoridadeCertificadora() throws IOException {
        Files.createFile(dir.resolve("index.txt"));
        Files.writeString(dir.resolve("crlnumber"), "1000\n");
        Files.writeString(
                dir.resolve("ca.cnf"),
                """
                [ ca ]
                default_ca = CA_default

                [ CA_default ]
                dir = %s
                database = $dir/index.txt
                certificate = $dir/ca.crt
                private_key = $dir/ca.key
                new_certs_dir = $dir
                serial = $dir/ca.srl
                crlnumber = $dir/crlnumber
                default_md = sha256
                default_crl_days = 1
                policy = policy_any

                [ policy_any ]
                commonName = supplied
                """
                        .formatted(dir));
    }

    private static byte[] gerarCrlReal(String nomeBase) throws IOException, InterruptedException {
        executar("openssl", "ca", "-config", "ca.cnf", "-gencrl", "-out", nomeBase + ".crl.pem");
        executar("openssl", "crl", "-in", nomeBase + ".crl.pem", "-outform", "DER", "-out", nomeBase + ".crl.der");
        return Files.readAllBytes(dir.resolve(nomeBase + ".crl.der"));
    }

    private static X509Certificate carregarCertificado(Path arquivo) throws IOException {
        try (var entrada = Files.newInputStream(arquivo)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(entrada);
        } catch (java.security.cert.CertificateException e) {
            throw new IllegalStateException("certificado de teste malformado: " + arquivo, e);
        }
    }

    /** Porta livre no momento da chamada, já fechada — usada só pra simular endpoint fora do ar (connection refused). */
    private static int portaLivreEFechada() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static void executar(String... comando) throws IOException, InterruptedException {
        Process processo = new ProcessBuilder(comando).directory(dir.toFile()).redirectErrorStream(true).start();
        String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int status = processo.waitFor();
        if (status != 0) {
            throw new IllegalStateException(
                    "comando de fixture falhou (" + status + "): " + String.join(" ", comando) + "\n" + saida);
        }
    }
}
