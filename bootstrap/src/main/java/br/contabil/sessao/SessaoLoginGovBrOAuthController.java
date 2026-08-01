package br.contabil.sessao;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.infra.observabilidade.CorrelacaoIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * BFF de <b>login</b> geral via OIDC gov.br (ADR-0035) — separado do BFF de assinatura
 * ({@code /assinatura/oauth}, ADR-0017): escopo {@code openid profile}, nunca
 * {@code sign}/{@code signature_session}. A asserção gov.br trocada aqui nunca chega
 * ao navegador; só um cookie de sessão {@code HttpOnly+Secure+SameSite=Lax} (o
 * {@code JSESSIONID} do container, ver {@code application.yml}) mais um cookie
 * anti-CSRF legível por JS ({@link CsrfCookieSessaoLogin}).
 *
 * <p>O callback é aberto pelo navegador do operador, não por um cliente {@code fetch}:
 * por isso toda falha responde com {@code 302} para a rota de login do SPA
 * ({@code frontend-erro-uri}, default {@code /entrar}) com {@code ?erro=<codigo>},
 * nunca com corpo JSON cru — espelhando a decisão 3 do ADR-0039 já aplicada ao BFF de
 * assinatura ({@link br.contabil.assinatura.AssinaturaGovBrOAuthController}). {@code codigo}
 * é sempre a taxonomia estável do {@code ErroContrato} §6.1; nenhum texto livre do gov.br
 * (nem material sensível) trafega na query string. O sucesso segue para
 * {@code post-login-redirect-uri} (RAZ-237).
 */
@RestController
@RequestMapping("/sessao/oauth")
final class SessaoLoginGovBrOAuthController {

    private static final Logger LOG = LoggerFactory.getLogger(SessaoLoginGovBrOAuthController.class);

    private final SessaoLoginGovBrOAuthProperties properties;
    private final RepositorioFluxoLoginGovBr repositorioFluxo;
    private final ClienteTokenSessaoLoginGovBr clienteToken;
    private final ServicoIdentidade servicoIdentidade;
    private final RepositorioAssercaoSessaoLoginGovBr repositorioAssercao;
    private final SecureRandom secureRandom;

    SessaoLoginGovBrOAuthController(
            SessaoLoginGovBrOAuthProperties properties,
            RepositorioFluxoLoginGovBr repositorioFluxo,
            ClienteTokenSessaoLoginGovBr clienteToken,
            ServicoIdentidade servicoIdentidade,
            RepositorioAssercaoSessaoLoginGovBr repositorioAssercao,
            SecureRandom secureRandom) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.repositorioFluxo = Objects.requireNonNull(repositorioFluxo, "repositorioFluxo");
        this.clienteToken = Objects.requireNonNull(clienteToken, "clienteToken");
        this.servicoIdentidade = Objects.requireNonNull(servicoIdentidade, "servicoIdentidade");
        this.repositorioAssercao = Objects.requireNonNull(repositorioAssercao, "repositorioAssercao");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @GetMapping("/iniciar")
    ResponseEntity<?> iniciar(HttpSession sessao) {
        properties.exigirConfiguracaoCompleta();
        RepositorioFluxoLoginGovBr.FluxoPendente fluxo = repositorioFluxo.iniciar(sessao);
        URI destino = UriComponentsBuilder.fromUri(Objects.requireNonNull(properties.authorizationUri(), "authorizationUri"))
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri().toString())
                .queryParam("scope", properties.scopesComoParametro())
                .queryParam("state", fluxo.state())
                .queryParam("code_challenge", codeChallenge(fluxo.codeVerifier()))
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, destino.toString()).build();
    }

    @GetMapping("/callback")
    ResponseEntity<?> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription,
            HttpServletRequest request,
            HttpSession sessao) {
        if (state == null || state.isBlank()) {
            return redirecionarParaLoginComErro("state_ausente");
        }
        RepositorioFluxoLoginGovBr.FluxoPendente fluxo;
        try {
            fluxo = repositorioFluxo.consumirFluxo(sessao, state);
        } catch (IllegalStateException e) {
            return redirecionarParaLoginComErro("state_invalido");
        }
        if (error != null && !error.isBlank()) {
            LOG.info("gov.br recusou OIDC de login: error={} error_description={}", error, errorDescription);
            return redirecionarParaLoginComErro("oauth_recusado");
        }
        if (code == null || code.isBlank()) {
            return redirecionarParaLoginComErro("code_ausente");
        }
        SessaoLoginGovBrOAuthToken token;
        try {
            token = clienteToken.trocarCodigoPorToken(code, fluxo.codeVerifier());
        } catch (IllegalStateException e) {
            SessaoLoginGovBrOAuthProvedorIndisponivelException falha =
                    new SessaoLoginGovBrOAuthProvedorIndisponivelException(e);
            String correlationId = CorrelacaoIds.atualOuNovo();
            LOG.error("Falha ao trocar code OIDC por token gov.br de login [correlationId={}]", correlationId, falha);
            return redirecionarParaLoginComErro(falha.codigo());
        }
        // Verifica que a assercao abre uma Sessao valida — falha fechado (propaga
        // NaoAutenticadoException/MfaRequeridoException/SemPermissaoException, ADR-0035
        // Decisao §1 e ADR-0030) antes de guardar qualquer coisa server-side.
        servicoIdentidade.autenticar(new ServicoIdentidade.CredencialGovBr(token.assercao()));
        repositorioAssercao.gravarAposLogin(request, token.assercao());
        ResponseCookie csrfCookie = CsrfCookieSessaoLogin.novoCookie(secureRandom);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, properties.postLoginRedirectUri().toString())
                .header(HttpHeaders.SET_COOKIE, csrfCookie.toString())
                .build();
    }

    @PostMapping("/sair")
    ResponseEntity<?> sair(HttpServletRequest request) {
        repositorioAssercao.encerrar(request.getSession(false));
        return ResponseEntity.noContent().build();
    }

    private static String codeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }

    /**
     * 302 para a rota de login do SPA com {@code ?erro=<codigo>}. {@code codigo} é sempre a taxonomia
     * estável do {@code ErroContrato} §6.1 — nunca texto livre vindo do gov.br (ADR-0039 decisão 3 /
     * R4 da ratificação RAZ-152, espelhado no login por RAZ-237).
     */
    private ResponseEntity<?> redirecionarParaLoginComErro(String codigo) {
        URI destino = UriComponentsBuilder.fromUri(
                        Objects.requireNonNull(properties.frontendErroUri(), "frontendErroUri"))
                .queryParam("erro", codigo)
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, destino.toString()).build();
    }
}
