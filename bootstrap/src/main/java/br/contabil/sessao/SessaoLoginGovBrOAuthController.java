package br.contabil.sessao;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * BFF de <b>login</b> geral via OIDC gov.br (ADR-0035) — separado do BFF de assinatura
 * ({@code /assinatura/oauth}, ADR-0017): escopo {@code openid profile}, nunca
 * {@code sign}/{@code signature_session}. A asserção gov.br trocada aqui nunca chega
 * ao navegador; só um cookie de sessão {@code HttpOnly+Secure+SameSite=Lax} (o
 * {@code JSESSIONID} do container, ver {@code application.yml}) mais um cookie
 * anti-CSRF legível por JS ({@link CsrfCookieSessaoLogin}).
 */
@RestController
@RequestMapping("/sessao/oauth")
final class SessaoLoginGovBrOAuthController {

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
            return ResponseEntity.badRequest().body(Map.of("erro", "state_ausente"));
        }
        RepositorioFluxoLoginGovBr.FluxoPendente fluxo;
        try {
            fluxo = repositorioFluxo.consumirFluxo(sessao, state);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", "state_invalido"));
        }
        if (error != null && !error.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "oauth_recusado", "detalhe", sanitizar(errorDescription, error)));
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "code_ausente"));
        }
        SessaoLoginGovBrOAuthToken token;
        try {
            token = clienteToken.trocarCodigoPorToken(code, fluxo.codeVerifier());
        } catch (IllegalStateException e) {
            throw new SessaoLoginGovBrOAuthProvedorIndisponivelException(e);
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

    private static String sanitizar(String valor, String fallback) {
        String texto = valor == null || valor.isBlank() ? fallback : valor;
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }
}
