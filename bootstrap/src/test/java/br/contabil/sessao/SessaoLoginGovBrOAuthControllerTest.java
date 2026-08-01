package br.contabil.sessao;

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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.util.UriComponentsBuilder;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

class SessaoLoginGovBrOAuthControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void iniciarRedirecionaParaGovBrComStatePkceEEscopoOpenidProfileSemExigirSessaoPrevia() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();

        var resposta = fixture.controller().iniciar(sessao);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String location = resposta.getHeaders().getLocation().toString();
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();

        assertThat(location).startsWith("https://sso.staging.acesso.gov.br/authorize?");
        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(query.getFirst("client_id")).isEqualTo("cliente-siafic-login");
        assertThat(query.getFirst("redirect_uri")).isEqualTo("http://localhost:8080/sessao/oauth/callback");
        assertThat(URLDecoder.decode(query.getFirst("scope"), StandardCharsets.UTF_8)).isEqualTo("openid profile");
        assertThat(query.getFirst("state")).isNotBlank();
        assertThat(query.getFirst("code_challenge")).isNotBlank();
        assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
    }

    @Test
    void callbackValidaStateTrocaCodeAutenticaEGuardaAssercaoComSessaoRegeneradaECookieCsrf() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);
        String idAntesDoCallback = sessao.getId();
        Sessao sessaoIam = sessaoAutenticada();
        fixture.servicoIdentidade().proximaSessao(sessaoIam);
        String state = stateDe(fixture.controller().iniciar(sessao));

        var resposta = fixture.controller().callback("codigo-autorizacao", state, null, null, request, sessao);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).isEqualTo(URI.create("/"));
        assertThat(resposta.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> cookie.startsWith("sessao-csrf=") && cookie.contains("SameSite=Lax"));
        assertThat(fixture.cliente().codeRecebido()).isEqualTo("codigo-autorizacao");
        assertThat(fixture.cliente().codeVerifierRecebido()).isNotBlank();
        assertThat(fixture.servicoIdentidade().assercaoRecebida()).isEqualTo("assercao-govbr");

        MockHttpSession sessaoRegenerada = (MockHttpSession) request.getSession(false);
        assertThat(sessaoRegenerada.getId()).isNotEqualTo(idAntesDoCallback);
        assertThat(fixture.repositorioAssercao().assercaoDaSessao(sessaoRegenerada)).contains("assercao-govbr");
    }

    @Test
    void callbackSemStateRedirecionaParaLoginComErroSemCorpoJson() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);

        var resposta = fixture.controller().callback("codigo", null, null, null, request, sessao);

        assertRedirecionaParaLoginComErro(resposta, "state_ausente");
        assertThat(fixture.repositorioAssercao().assercaoDaSessao(sessao)).isEmpty();
    }

    @Test
    void callbackComStateDiferenteRedirecionaParaLoginComErroNaoAutenticaNemGuardaAssercao() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);
        fixture.controller().iniciar(sessao);

        var resposta = fixture.controller().callback("codigo", "state-invasor", null, null, request, sessao);

        assertRedirecionaParaLoginComErro(resposta, "state_invalido");
        assertThat(fixture.repositorioAssercao().assercaoDaSessao(sessao)).isEmpty();
    }

    @Test
    void callbackComErroDoGovBrRedirecionaComOauthRecusadoSemVazarTextoLivre() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);
        String state = stateDe(fixture.controller().iniciar(sessao));

        var resposta = fixture.controller().callback(
                null, state, "access_denied", "usuario cancelou <script>", request, sessao);

        assertRedirecionaParaLoginComErro(resposta, "oauth_recusado");
        // taxonomia estável apenas — nenhum texto livre do gov.br trafega na query string (R4 RAZ-152)
        assertThat(resposta.getHeaders().getLocation().toString())
                .doesNotContain("access_denied")
                .doesNotContain("usuario", "cancelou", "script");
        assertThat(fixture.repositorioAssercao().assercaoDaSessao(sessao)).isEmpty();
    }

    @Test
    void callbackSemCodeRedirecionaComCodeAusente() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);
        String state = stateDe(fixture.controller().iniciar(sessao));

        var resposta = fixture.controller().callback(null, state, null, null, request, sessao);

        assertRedirecionaParaLoginComErro(resposta, "code_ausente");
        assertThat(fixture.repositorioAssercao().assercaoDaSessao(sessao)).isEmpty();
    }

    @Test
    void callbackComFalhaDoProvedorGovBrRedirecionaComCodigoProvedorIndisponivelSemGuardarAssercao() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);
        fixture.cliente().falharComProximaChamada();
        String state = stateDe(fixture.controller().iniciar(sessao));

        var resposta = fixture.controller().callback("codigo-autorizacao", state, null, null, request, sessao);

        assertRedirecionaParaLoginComErro(resposta, "login_oauth_provedor_indisponivel");
        assertThat(fixture.repositorioAssercao().assercaoDaSessao(sessao)).isEmpty();
    }

    @Test
    void callbackComServicoIdentidadeRecusandoAssercaoPropagaSemGuardarNadaServerSide() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);
        // ServicoIdentidadeFake sem proximaSessao configurada -> NaoAutenticadoException (asserção inválida/expirada)
        String state = stateDe(fixture.controller().iniciar(sessao));

        assertThatExceptionOfType(ServicoIdentidade.NaoAutenticadoException.class)
                .isThrownBy(() -> fixture.controller().callback("codigo-autorizacao", state, null, null, request, sessao));

        assertThat(fixture.repositorioAssercao().assercaoDaSessao(sessao)).isEmpty();
    }

    @Test
    void sairEncerraSessaoServerSideDescartandoAssercaoGuardada() {
        var fixture = fixture();
        MockHttpSession sessao = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);
        fixture.servicoIdentidade().proximaSessao(sessaoAutenticada());
        String state = stateDe(fixture.controller().iniciar(sessao));
        fixture.controller().callback("codigo-autorizacao", state, null, null, request, sessao);
        MockHttpSession sessaoLogada = (MockHttpSession) request.getSession(false);

        ResponseEntity<?> resposta = fixture.controller().sair(request);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(sessaoLogada.isInvalid()).isTrue();
    }

    private static String stateDe(ResponseEntity<?> resposta) {
        return UriComponentsBuilder.fromUriString(resposta.getHeaders().getLocation().toString())
                .build()
                .getQueryParams()
                .getFirst("state");
    }

    /** Contrato comum dos ramos de erro do callback: 302 para /entrar?erro=<codigo>, nunca corpo JSON (RAZ-237/ADR-0039 §3). */
    private static void assertRedirecionaParaLoginComErro(ResponseEntity<?> resposta, String codigoEsperado) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getBody()).isNull();
        String location = resposta.getHeaders().getLocation().toString();
        assertThat(location).startsWith("/entrar");
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(query.getFirst("erro")).isEqualTo(codigoEsperado);
    }

    private static Fixture fixture() {
        SessaoLoginGovBrOAuthProperties properties = new SessaoLoginGovBrOAuthProperties(
                URI.create("https://sso.staging.acesso.gov.br/authorize"),
                URI.create("https://sso.staging.acesso.gov.br/token"),
                "cliente-siafic-login",
                "segredo",
                URI.create("http://localhost:8080/sessao/oauth/callback"),
                URI.create("/"),
                URI.create("/entrar"),
                List.of("openid", "profile"),
                Duration.ofMinutes(10));
        var repositorioFluxo = new RepositorioFluxoLoginGovBr(new SecureRandom(), CLOCK, properties);
        var cliente = new ClienteFake();
        var servicoIdentidade = new ServicoIdentidadeFake();
        var repositorioAssercao = new RepositorioAssercaoSessaoLoginGovBr();
        var controller = new SessaoLoginGovBrOAuthController(
                properties, repositorioFluxo, cliente, servicoIdentidade, repositorioAssercao, new SecureRandom());
        return new Fixture(controller, cliente, servicoIdentidade, repositorioAssercao);
    }

    private static Sessao sessaoAutenticada() {
        return new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                new TenantId(UUID.randomUUID()),
                Optional.empty(),
                true,
                CLOCK.instant().plusSeconds(300));
    }

    private record Fixture(
            SessaoLoginGovBrOAuthController controller,
            ClienteFake cliente,
            ServicoIdentidadeFake servicoIdentidade,
            RepositorioAssercaoSessaoLoginGovBr repositorioAssercao) {}

    private static final class ClienteFake implements ClienteTokenSessaoLoginGovBr {

        private String codeRecebido;
        private String codeVerifierRecebido;
        private boolean falharNaProximaChamada;

        void falharComProximaChamada() {
            this.falharNaProximaChamada = true;
        }

        @Override
        public SessaoLoginGovBrOAuthToken trocarCodigoPorToken(String code, String codeVerifier) {
            if (falharNaProximaChamada) {
                throw new IllegalStateException("Token endpoint gov.br de login respondeu status 503");
            }
            this.codeRecebido = code;
            this.codeVerifierRecebido = codeVerifier;
            return new SessaoLoginGovBrOAuthToken("assercao-govbr", CLOCK.instant().plusSeconds(300));
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
