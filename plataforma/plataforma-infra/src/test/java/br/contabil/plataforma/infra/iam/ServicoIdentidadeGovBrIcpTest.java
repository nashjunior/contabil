package br.contabil.plataforma.infra.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.CredencialGovBr;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServicoIdentidadeGovBrIcpTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CPF = "12345678901";
    private static final UUID ENTE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

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
        assertThat(servico.autorizar(sessao, new Recurso("razao:fato_contabil"), Acao.APROVAR)).isFalse();
    }

    @Test
    @DisplayName("gov.br Bronze autentica sem MFA forte — ControleAcesso bloqueará ação que movimenta recurso")
    void bronzeNaoMarcaMfaForte() throws Exception {
        var sessao = servico.autenticar(new CredencialGovBr(jwt("BRONZE")));

        assertThat(sessao.mfaConcluido()).isFalse();
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

    private IamProperties propertiesComCpf(String cpf, Set<IamProperties.Papel> papeis) {
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
                IamProperties.Icp.vazio(),
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
