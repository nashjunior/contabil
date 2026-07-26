package br.contabil.plataforma.infra.assinatura;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Teste manual/staging-gated (RAZ-74, item b) — chama de verdade
 * {@code https://assinatura-api.staging.iti.br} através de {@link ProvedorAssinaturaGovBrHttp},
 * sem {@code HttpClient} mockado (isso já é coberto por {@link ProvedorAssinaturaGovBrHttpTest},
 * que só prova a montagem da requisição contra um servidor local).
 *
 * <p><b>Desligado por padrão — nunca roda em CI</b> ({@code @EnabledIfEnvironmentVariable}): exige
 * um token Bearer real, obtido pelo fluxo OAuth2 interativo do signatário (escopo {@code sign}
 * ou {@code signature_session}) que este pacote deliberadamente não implementa (ver
 * {@code package-info} e {@link ProvedorAssinaturaGovBr}) — não há credencial de máquina-a-máquina
 * para essa chamada, só a sessão de um cidadão autenticado. RAZ-39 (provisionamento das
 * credenciais OAuth2 gov.br staging) está concluída, mas o token de um signatário de teste é
 * obtido manualmente a cada execução (expira) e não fica commitado neste repositório.
 *
 * <p>Para rodar manualmente: completar o fluxo OAuth2 (ex.: via
 * {@code bootstrap/.../AssinaturaGovBrOAuthController} subindo o bootstrap, ou o passo a passo do
 * manual de integração oficial) para obter um token de acesso e exportar
 * {@code SIAFIC_ASSINATURA_GOVBR_STAGING_TOKEN=<token>} antes de rodar este teste (opcionalmente
 * {@code SIAFIC_ASSINATURA_GOVBR_STAGING_BASE_URI} para apontar a outro ambiente).
 */
@EnabledIfEnvironmentVariable(named = "SIAFIC_ASSINATURA_GOVBR_STAGING_TOKEN", matches = ".+")
class ProvedorAssinaturaGovBrHttpStagingTest {

    @Test
    @DisplayName("assinarPkcs7 contra o endpoint staging real do gov.br: 200 com PKCS#7 ou 403 de conta inelegível — nunca mockado")
    void chamaEndpointStagingReal() {
        String token = System.getenv("SIAFIC_ASSINATURA_GOVBR_STAGING_TOKEN");
        URI baseUri = URI.create(System.getenv()
                .getOrDefault("SIAFIC_ASSINATURA_GOVBR_STAGING_BASE_URI", "https://assinatura-api.staging.iti.br"));

        var provedor = new ProvedorAssinaturaGovBrHttp(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), baseUri, () -> token);

        // Conteúdo sintético só pra ter um hash SHA-256 válido de verdade — não é um documento
        // real (RAZ-37: sem PII), o que importa aqui é a chamada HTTP em si.
        byte[] hash = sha256(("raz-74-staging-smoke-" + UUID.randomUUID()).getBytes());

        try {
            byte[] pkcs7 = provedor.assinarPkcs7(hash);
            assertThat(pkcs7).isNotEmpty();
        } catch (ProvedorAssinaturaGovBr.ContaGovBrNaoElegivelException e) {
            // Conta de teste sem nível Prata/Ouro (ou CPF com situação): ainda prova que o
            // endpoint real do gov.br respondeu e que o contrato 403 -> exceção de domínio
            // (não mascarado por mock) está correto de ponta a ponta.
            assertThat(e.getMessage()).isNotBlank();
        }
    }

    private static byte[] sha256(byte[] entrada) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(entrada);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
