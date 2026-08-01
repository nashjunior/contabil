package br.contabil.plataforma.infra.iam;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.CredencialCertificadoIcp;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.CredencialGovBr;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;

class ServicoIdentidadeGovBrIcpTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CPF = "12345678901";
    private static final UUID ENTE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CERTIFICADO_ICP_SHA256 =
            "C9:14:60:13:AA:87:75:CE:EF:F1:13:1F:2A:F8:F3:48:8F:D1:D8:50:9A:03:B8:88:61:73:D0:E3:7A:67:24:DE";
    private static final String CERTIFICADO_ICP_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDRzCCAi+gAwIBAgIUXiDlUYadD2W8B283twEY5k5f/BgwDQYJKoZIhvcNAQEL
            BQAwMzEbMBkGA1UEAwwSU2Vydmlkb3IgVGVzdGUgSUNQMRQwEgYDVQQFEwsxMjM0
            NTY3ODkwMTAeFw0yNjA3MjYxNjUxMDZaFw0zNjA3MjMxNjUxMDZaMDMxGzAZBgNV
            BAMMElNlcnZpZG9yIFRlc3RlIElDUDEUMBIGA1UEBRMLMTIzNDU2Nzg5MDEwggEi
            MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDXQ+5cpyO5OGRlV0R89/tXCQhc
            Cb8+KdwDETp9SjbrQyfP9k8+ktwvEM/4Yjx/mCL7jpxBf79jJ0c2B+ua06708Qmp
            V301/4i8KSAcaexnATV0PdR/G0p4e4N+QkMn5KDtHVBM11orWpciBEdUftw57Fr1
            sgGe9X4qR6R7x1QdA/ugnCoyeKge06r7v25KMWMQOZgRZA05JRCenhylkmH8jF+C
            91mvh6YPn3plunKDJW83i2v1wm+Daf/qyu/o9k5EoSSc7n97WP+VwhHO/Z+eVOYI
            nt0lLhbRkD/9V5RNlwrdhpY+lioG3toDDaJiI+xemvhQRWleX4IChBOh2SanAgMB
            AAGjUzBRMB0GA1UdDgQWBBQWF6BoHUFZAMYcEM46KE0EO1ki5TAfBgNVHSMEGDAW
            gBQWF6BoHUFZAMYcEM46KE0EO1ki5TAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3
            DQEBCwUAA4IBAQB+uSvlAWoecZ65pk0n97ObZ4gamoOoKylmOSazVip2XxztOCmy
            AGC5Gi8jcll8XCkCx2SefMIf7nMumpMR/K2kBmT6IAayROj/j/E1KTnv8C/k5nMJ
            xJX+NQ8hmbZ5hBReJgnMm8253SQ0yIidXIP2MQ4/J1J52F8zuJ2od0xzBzmRGSkC
            69OuSeqPgcoMiN4TPecvwZb/Ugilr5QyPuHP8rD7i/bajuGwieSAJ8Hpa65NkOrd
            HEjOwULS/FNu3rrDE+AQ4XuHvUytkuz2WpRC22Hj7MCP/Q9c56rUxpxr0z7iaAQ7
            aKJ3pPwWMCUC+chhld5waN6a93VGNrb4f6D0
            -----END CERTIFICATE-----
            """;

    private KeyPair keyPair;
    private IamProperties properties;
    private ServicoIdentidade servico;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        properties = properties(Set.of(IamProperties.Papel.LANCADOR));
        servico = servico(properties);
    }

    @Test
    @DisplayName("gov.br: JWT RS256 verificado abre sessão nominal por CPF/ente e autoriza só o papel concedido")
    void autenticaGovBrComRbac() throws Exception {
        var sessao = servico.autenticar(new CredencialGovBr(jwt("OURO")));

        assertThat(sessao.titular().numero()).isEqualTo(CPF);
        assertThat(sessao.ente().valor()).isEqualTo(ENTE_ID);
        assertThat(sessao.mfaConcluido()).isTrue();
        assertThat(servico.autorizar(sessao, new Recurso("razao:fato_contabil"), Acao.CRIAR)).isTrue();
        assertThat(servico.autorizar(sessao, new Recurso("execucao:empenho"), Acao.CRIAR)).isTrue();
        assertThat(servico.autorizar(sessao, new Recurso("razao:fato_contabil"), Acao.APROVAR)).isFalse();
        assertThat(servico.autorizar(sessao, new Recurso("execucao:empenho"), Acao.ASSINAR)).isFalse();
    }

    @Test
    @DisplayName("gov.br Bronze autentica sem MFA forte — ControleAcesso bloqueará ação que movimenta recurso")
    void bronzeNaoMarcaMfaForte() throws Exception {
        var sessao = servico.autenticar(new CredencialGovBr(jwt("BRONZE")));

        assertThat(sessao.mfaConcluido()).isFalse();
    }

    @Test
    @DisplayName("ICP-Brasil: certificado com fingerprint SHA-256 provisionado abre sessão com MFA forte")
    void autenticaCertificadoIcpPorAllowlistDeFingerprint() {
        ServicoIdentidade servicoComIcp = servico(propertiesComIcp(Set.of(CERTIFICADO_ICP_SHA256)));

        var sessao = servicoComIcp.autenticar(new CredencialCertificadoIcp(CERTIFICADO_ICP_PEM));

        assertThat(sessao.titular().numero()).isEqualTo(CPF);
        assertThat(sessao.ente().valor()).isEqualTo(ENTE_ID);
        assertThat(sessao.mfaConcluido()).isTrue();
        assertThat(sessao.expiraEm()).isEqualTo(Instant.parse("2026-07-27T13:00:00Z"));
        assertThat(servicoComIcp.autorizar(sessao, new Recurso("razao:fato_contabil"), Acao.CRIAR)).isTrue();
    }

    @Test
    @DisplayName("ICP-Brasil: certificado fora da allowlist é nao_autenticado")
    void rejeitaCertificadoIcpSemFingerprintProvisionado() {
        ServicoIdentidade servicoSemIcpProvisionado = servico(propertiesComIcp(Set.of()));

        assertThatExceptionOfType(ServicoIdentidade.NaoAutenticadoException.class)
                .isThrownBy(() -> servicoSemIcpProvisionado.autenticar(new CredencialCertificadoIcp(CERTIFICADO_ICP_PEM)))
                .withMessageContaining("nao provisionado")
                .withMessageNotContaining(CPF);
    }

    @Test
    @DisplayName("gov.br: JWT adulterado é nao_autenticado")
    void rejeitaJwtAdulterado() throws Exception {
        String token = jwt("OURO");
        String adulterado = token.substring(0, token.length() - 3) + "abc";

        assertThatExceptionOfType(ServicoIdentidade.NaoAutenticadoException.class)
                .isThrownBy(() -> servico.autenticar(new CredencialGovBr(adulterado)))
                .withMessageContaining("assinatura");
    }

    @Test
    @DisplayName("RBAC é deny-by-default quando CPF/ente não tem concessão")
    void rbacDenyByDefault() throws Exception {
        // CPF sintético sem checksum válido (13-nfr §piso "sem PII real em não-produção"),
        // mesmo padrão do restante do repo — só precisa ser diferente da constante CPF acima.
        IamProperties semConcessaoDoCpf = propertiesComCpf("11122233366", Set.of(IamProperties.Papel.LANCADOR));
        ServicoIdentidade servicoSemConcessao = servico(semConcessaoDoCpf);

        assertThatExceptionOfType(ServicoIdentidade.SemPermissaoException.class)
                .isThrownBy(() -> servicoSemConcessao.autenticar(new CredencialGovBr(jwt("OURO"))))
                .withMessageContaining("***.456.***-**")
                .withMessageNotContaining(CPF);
    }

    @Test
    @DisplayName("matriz SoD rejeita acúmulo lança + autoriza preventivamente")
    void rejeitaPapeisConflitantes() {
        IamProperties conflitado = properties(Set.of(IamProperties.Papel.LANCADOR, IamProperties.Papel.AUTORIZADOR));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(conflitado::validar)
                .withMessageContaining("segregacao de funcoes")
                .withMessageContaining("***.456.***-**")
                .withMessageNotContaining(CPF);
    }

    @Test
    @DisplayName("RAZ-218: LANCADOR ganha CRIAR sobre execucao:liquidacao (mesmo papel que empenha)")
    void lancadorAutorizaLiquidacaoCriar() throws Exception {
        var sessao = servico.autenticar(new CredencialGovBr(jwt("OURO")));

        assertThat(servico.autorizar(sessao, new Recurso("execucao:liquidacao"), Acao.CRIAR)).isTrue();
    }

    @Test
    @DisplayName("RAZ-218/ADR-0053: LANCADOR NÃO ganha execucao:dotacao — quem fixa o teto não é quem gasta contra ele")
    void lancadorNaoAutorizaDotacao() throws Exception {
        var sessao = servico.autenticar(new CredencialGovBr(jwt("OURO")));

        assertThat(servico.autorizar(sessao, new Recurso("execucao:dotacao"), Acao.CRIAR)).isFalse();
        assertThat(servico.autorizar(sessao, new Recurso("execucao:dotacao"), Acao.ALTERAR)).isFalse();
    }

    @Test
    @DisplayName("RAZ-218/ADR-0053: ADMIN_PLATAFORMA autoriza CRIAR (fixação/LOA) e ALTERAR (crédito adicional) sobre execucao:dotacao")
    void adminPlataformaAutorizaDotacaoCriarEAlterar() throws Exception {
        ServicoIdentidade servicoAdmin = servico(properties(Set.of(IamProperties.Papel.ADMIN_PLATAFORMA)));
        var sessao = servicoAdmin.autenticar(new CredencialGovBr(jwt("OURO")));

        assertThat(servicoAdmin.autorizar(sessao, new Recurso("execucao:dotacao"), Acao.CRIAR)).isTrue();
        assertThat(servicoAdmin.autorizar(sessao, new Recurso("execucao:dotacao"), Acao.ALTERAR)).isTrue();
        assertThat(servicoAdmin.autorizar(sessao, new Recurso("execucao:liquidacao"), Acao.CRIAR)).isFalse();
    }

    private ServicoIdentidade servico(IamProperties props) {
        props.validar();
        return new ServicoIdentidadeGovBrIcp(
                new VerificadorJwtGovBr(JSON, CLOCK, props.govbr()),
                new VerificadorCertificadoIcp(CLOCK, props.icp()),
                props,
                CLOCK);
    }

    private IamProperties properties(Set<IamProperties.Papel> papeis) {
        return propertiesComCpf(CPF, papeis);
    }

    private IamProperties propertiesComIcp(Set<String> fingerprintsSha256) {
        return properties(CPF, Set.of(IamProperties.Papel.LANCADOR), new IamProperties.Icp(fingerprintsSha256));
    }

    private IamProperties propertiesComCpf(String cpf, Set<IamProperties.Papel> papeis) {
        return properties(cpf, papeis, IamProperties.Icp.vazio());
    }

    private IamProperties properties(String cpf, Set<IamProperties.Papel> papeis, IamProperties.Icp icp) {
        return new IamProperties(
                true,
                new IamProperties.GovBr(
                        URI.create("https://sso.staging.acesso.gov.br"),
                        "siafic-razao",
                        publicKeyPem(),
                        "cpf",
                        "ente_id",
                        "orgao",
                        "nivel_conta"),
                icp,
                Duration.ofHours(1),
                List.of(new IamProperties.Concessao(cpf, ENTE_ID, null, papeis)));
    }

    private String jwt(String nivelConta) throws Exception {
        String header = base64Url(JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
        String payload = base64Url(JSON.writeValueAsBytes(Map.of(
                "iss", "https://sso.staging.acesso.gov.br",
                "aud", "siafic-razao",
                "exp", Instant.now(CLOCK).plusSeconds(600).getEpochSecond(),
                "cpf", CPF,
                "ente_id", ENTE_ID.toString(),
                "nivel_conta", nivelConta)));
        String assinatura = assinar(header + "." + payload);
        return header + "." + payload + "." + assinatura;
    }

    private String assinar(String signingInput) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return base64Url(signature.sign());
    }

    private String publicKeyPem() {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
