package br.contabil.assinatura;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

class AssinaturaGovBrOAuthControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);
    private static final URI FRONTEND_RETORNO_URI = URI.create("http://localhost:5173/execucao/assinatura/retorno");

    @AfterEach
    void limparRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void iniciarRedirecionaParaGovBrComStatePkceEscoposEEnteNaSessao() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        TenantId ente = new TenantId(UUID.randomUUID());
        fixture.sessoesIam().gravarVerificada(sessao, sessaoAutenticada(ente));

        var resposta = fixture.controller().iniciar(sessao);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String location = resposta.getHeaders().getLocation().toString();
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();

        assertThat(location).startsWith("https://cas.staging.iti.br/oauth2.0/authorize?");
        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(query.getFirst("client_id")).isEqualTo("cliente-siafic");
        assertThat(query.getFirst("redirect_uri")).isEqualTo("http://localhost:8080/assinatura/oauth/callback");
        assertThat(URLDecoder.decode(query.getFirst("scope"), StandardCharsets.UTF_8))
                .isEqualTo("signature_session");
        assertThat(query.getFirst("state")).isNotBlank();
        assertThat(query.getFirst("code_challenge")).isNotBlank();
        assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
    }

    @Test
    void callbackValidaStateTrocaCodeERedirecionaParaFrontendComResultadoOk() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        TenantId ente = new TenantId(UUID.randomUUID());
        fixture.sessoesIam().gravarVerificada(sessao, sessaoAutenticada(ente));
        fixture.servicoIdentidade().proximaSessao(sessaoAutenticada(ente));
        String state = stateDe(fixture.controller().iniciar(sessao));

        var resposta = fixture.controller().callback("codigo-autorizacao", state, null, null, sessao);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String location = resposta.getHeaders().getLocation().toString();
        assertThat(location).startsWith(FRONTEND_RETORNO_URI.toString());
        assertThat(queryDe(resposta).getFirst("resultado")).isEqualTo("ok");
        assertThat(queryDe(resposta).getFirst("codigo")).isNull();
        assertThat(fixture.cliente().codeRecebido()).isEqualTo("codigo-autorizacao");
        assertThat(fixture.cliente().codeVerifierRecebido()).isNotBlank();

        // sucesso ainda grava token/sessao mesmo devolvendo 302 em vez de 204 (ADR-0039 decisao 3)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(new AssinaturaGovBrTokenSessaoSupplier(fixture.repositorio()).get())
                .isEqualTo("access-token-govbr");
        assertThat(new ResolvedorSessaoAssinaturaGovBrHttpSession(fixture.sessoesIam()).enteAutenticado(sessao))
                .isEqualTo(ente);
        assertThat(fixture.servicoIdentidade().assercaoRecebida()).isEqualTo("access-token-govbr");
    }

    @Test
    void callbackSemStateRedirecionaComCodigoStateAusente() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();

        var resposta = fixture.controller().callback("codigo", null, null, null, sessao);

        assertRedirecionaComErro(resposta, "state_ausente");
    }

    @Test
    void callbackComStateDiferenteRedirecionaComCodigoStateInvalidoENaoGuardaToken() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        fixture.sessoesIam().gravarVerificada(sessao, sessaoAutenticada(new TenantId(UUID.randomUUID())));
        fixture.controller().iniciar(sessao);

        var resposta = fixture.controller().callback("codigo", "state-invasor", null, null, sessao);

        assertRedirecionaComErro(resposta, "state_invalido");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var supplier = new AssinaturaGovBrTokenSessaoSupplier(fixture.repositorio());
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(supplier::get)
                .withMessageContaining("ainda nao concluiu OAuth2");
    }

    @Test
    void callbackComErrorDoGovBrRedirecionaComCodigoOauthRecusado() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        TenantId ente = new TenantId(UUID.randomUUID());
        fixture.sessoesIam().gravarVerificada(sessao, sessaoAutenticada(ente));
        String state = stateDe(fixture.controller().iniciar(sessao));

        var resposta = fixture.controller()
                .callback(null, state, "access_denied", "Usuario recusou a solicitacao", sessao);

        assertRedirecionaComErro(resposta, "oauth_recusado");
    }

    @Test
    void callbackSemCodeRedirecionaComCodigoCodeAusente() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        TenantId ente = new TenantId(UUID.randomUUID());
        fixture.sessoesIam().gravarVerificada(sessao, sessaoAutenticada(ente));
        String state = stateDe(fixture.controller().iniciar(sessao));

        var resposta = fixture.controller().callback(null, state, null, null, sessao);

        assertRedirecionaComErro(resposta, "code_ausente");
    }

    @Test
    void callbackComFalhaDoProvedorGovBrRedirecionaComCodigoProvedorIndisponivelENaoGuardaToken() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        TenantId ente = new TenantId(UUID.randomUUID());
        fixture.sessoesIam().gravarVerificada(sessao, sessaoAutenticada(ente));
        fixture.cliente().falharComProximaChamada();
        String state = stateDe(fixture.controller().iniciar(sessao));

        var resposta = fixture.controller().callback("codigo-autorizacao", state, null, null, sessao);

        assertRedirecionaComErro(resposta, "oauth_provedor_indisponivel");

        // nao guarda token nenhum apos falha do provedor
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var supplier = new AssinaturaGovBrTokenSessaoSupplier(fixture.repositorio());
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(supplier::get);
    }

    @Test
    void callbackSemFrontendRetornoUriConfiguradoFalhaFechado() {
        var fixture = fixture(null);
        MockHttpSession sessao = new MockHttpSession();

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> fixture.controller().callback("codigo", "state-qualquer", null, null, sessao))
                .withMessageContaining("frontend-retorno-uri");
    }

    @Test
    void iniciarSemSessaoIamVerificadaPropagaNaoAutenticadoSemMapearNoController() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();

        assertThatExceptionOfType(ServicoIdentidade.NaoAutenticadoException.class)
                .isThrownBy(() -> fixture.controller().iniciar(sessao));
    }

    @Test
    void callbackComIamDeOutroEnteRedirecionaComCodigoEnteDivergenteENaoGuardaTokenNemTrocaSessao() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        TenantId enteDaSessao = new TenantId(UUID.randomUUID());
        TenantId enteDoTokenGovBr = new TenantId(UUID.randomUUID());
        Sessao sessaoOriginal = sessaoAutenticada(enteDaSessao);
        fixture.sessoesIam().gravarVerificada(sessao, sessaoOriginal);
        fixture.servicoIdentidade().proximaSessao(sessaoAutenticada(enteDoTokenGovBr));
        String state = stateDe(fixture.controller().iniciar(sessao));

        var resposta = fixture.controller().callback("codigo", state, null, null, sessao);

        assertRedirecionaComErro(resposta, "ente_divergente");
        assertThat(new ResolvedorSessaoAssinaturaGovBrHttpSession(fixture.sessoesIam()).enteAutenticado(sessao))
                .isEqualTo(enteDaSessao);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var supplier = new AssinaturaGovBrTokenSessaoSupplier(fixture.repositorio());
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(supplier::get)
                .withMessageContaining("ainda nao concluiu OAuth2");
    }

    @Test
    void sessaoIamGravadaNaHttpSessionESerializavel() throws Exception {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        TenantId ente = new TenantId(UUID.randomUUID());
        fixture.sessoesIam().gravarVerificada(sessao, sessaoAutenticada(ente));

        Object atributo = sessao.getAttribute(SessaoIamAssinaturaHttpSession.ATTR_SESSAO_IAM);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream saida = new ObjectOutputStream(buffer)) {
            saida.writeObject(atributo);
        }
        Object restaurado;
        try (ObjectInputStream entrada = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
            restaurado = entrada.readObject();
        }

        assertThat(restaurado).isInstanceOf(SessaoIamAssinaturaHttpSession.SessaoVerificada.class);
        assertThat(((SessaoIamAssinaturaHttpSession.SessaoVerificada) restaurado).ente()).isEqualTo(ente);
    }

    private static String stateDe(org.springframework.http.ResponseEntity<?> resposta) {
        return UriComponentsBuilder.fromUriString(resposta.getHeaders().getLocation().toString())
                .build()
                .getQueryParams()
                .getFirst("state");
    }

    private static org.springframework.util.MultiValueMap<String, String> queryDe(
            org.springframework.http.ResponseEntity<?> resposta) {
        return UriComponentsBuilder.fromUriString(resposta.getHeaders().getLocation().toString())
                .build()
                .getQueryParams();
    }

    /** Confere o contrato comum dos ramos de erro do callback: 302 + resultado=erro + codigo (nunca corpo JSON). */
    private static void assertRedirecionaComErro(org.springframework.http.ResponseEntity<?> resposta, String codigoEsperado) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getBody()).isNull();
        String location = resposta.getHeaders().getLocation().toString();
        assertThat(location).startsWith(FRONTEND_RETORNO_URI.toString());
        var query = queryDe(resposta);
        assertThat(query.getFirst("resultado")).isEqualTo("erro");
        assertThat(query.getFirst("codigo")).isEqualTo(codigoEsperado);
    }

    private static Fixture fixture() {
        return fixture(FRONTEND_RETORNO_URI);
    }

    private static Fixture fixture(URI frontendRetornoUri) {
        AssinaturaGovBrOAuthProperties properties = new AssinaturaGovBrOAuthProperties(
                URI.create("https://cas.staging.iti.br/oauth2.0/authorize"),
                URI.create("https://cas.staging.iti.br/oauth2.0/accessToken"),
                "cliente-siafic",
                "segredo",
                URI.create("http://localhost:8080/assinatura/oauth/callback"),
                frontendRetornoUri,
                List.of("signature_session"),
                Duration.ofMinutes(10));
        var repositorio = new RepositorioSessaoAssinaturaGovBr(new SecureRandom(), CLOCK, properties);
        var cliente = new ClienteFake();
        var sessoesIam = new SessaoIamAssinaturaHttpSession(CLOCK);
        var resolvedor = new ResolvedorSessaoAssinaturaGovBrHttpSession(sessoesIam);
        var servicoIdentidade = new ServicoIdentidadeFake();
        var controller =
                new AssinaturaGovBrOAuthController(properties, repositorio, cliente, resolvedor, servicoIdentidade, sessoesIam);
        return new Fixture(controller, repositorio, cliente, servicoIdentidade, sessoesIam);
    }

    private static Sessao sessaoAutenticada(TenantId ente) {
        return new Sessao(
                UUID.randomUUID(),
                new Cpf("cpf-sintetico"),
                ente,
                Optional.empty(),
                true,
                CLOCK.instant().plusSeconds(300));
    }

    private record Fixture(
            AssinaturaGovBrOAuthController controller,
            RepositorioSessaoAssinaturaGovBr repositorio,
            ClienteFake cliente,
            ServicoIdentidadeFake servicoIdentidade,
            SessaoIamAssinaturaHttpSession sessoesIam) {}

    private static final class ClienteFake implements ClienteTokenAssinaturaGovBr {

        private String codeRecebido;
        private String codeVerifierRecebido;
        private boolean falharNaProximaChamada;

        void falharComProximaChamada() {
            this.falharNaProximaChamada = true;
        }

        @Override
        public AssinaturaGovBrOAuthToken trocarCodigoPorToken(String code, String codeVerifier) {
            if (falharNaProximaChamada) {
                throw new IllegalStateException("Token endpoint gov.br respondeu status 503");
            }
            this.codeRecebido = code;
            this.codeVerifierRecebido = codeVerifier;
            return new AssinaturaGovBrOAuthToken("access-token-govbr", CLOCK.instant().plusSeconds(300));
        }

        private String codeRecebido() {
            return codeRecebido;
        }

        private String codeVerifierRecebido() {
            return codeVerifierRecebido;
        }
    }

    private static final class ServicoIdentidadeFake implements ServicoIdentidade {

        private Sessao proximaSessao;
        private String assercaoRecebida;

        void proximaSessao(Sessao proximaSessao) {
            this.proximaSessao = proximaSessao;
        }

        String assercaoRecebida() {
            return assercaoRecebida;
        }

        @Override
        public Sessao autenticar(Credencial credencial) {
            if (credencial instanceof CredencialGovBr govBr) {
                this.assercaoRecebida = govBr.assercao();
            }
            if (proximaSessao == null) {
                throw new NaoAutenticadoException("sem sessao IAM de teste");
            }
            return proximaSessao;
        }

        @Override
        public boolean autorizar(Sessao sessao, Recurso recurso, Acao acao) {
            return true;
        }

        @Override
        public Sessao completarMfa(DesafioMfa desafio, RespostaMfa resposta) {
            throw new UnsupportedOperationException("MFA fora deste teste");
        }
    }
}
