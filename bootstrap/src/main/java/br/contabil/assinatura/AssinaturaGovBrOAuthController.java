package br.contabil.assinatura;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/assinatura/oauth")
final class AssinaturaGovBrOAuthController {

    private final AssinaturaGovBrOAuthProperties properties;
    private final RepositorioSessaoAssinaturaGovBr repositorio;
    private final ClienteTokenAssinaturaGovBr clienteToken;
    private final ResolvedorSessaoAssinaturaGovBr resolvedorSessao;
    private final ServicoIdentidade servicoIdentidade;
    private final SessaoIamAssinaturaHttpSession sessoesIam;

    AssinaturaGovBrOAuthController(
            AssinaturaGovBrOAuthProperties properties,
            RepositorioSessaoAssinaturaGovBr repositorio,
            ClienteTokenAssinaturaGovBr clienteToken,
            ResolvedorSessaoAssinaturaGovBr resolvedorSessao,
            ServicoIdentidade servicoIdentidade,
            SessaoIamAssinaturaHttpSession sessoesIam) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.clienteToken = Objects.requireNonNull(clienteToken, "clienteToken");
        this.resolvedorSessao = Objects.requireNonNull(resolvedorSessao, "resolvedorSessao");
        this.servicoIdentidade = Objects.requireNonNull(servicoIdentidade, "servicoIdentidade");
        this.sessoesIam = Objects.requireNonNull(sessoesIam, "sessoesIam");
    }

    @GetMapping("/iniciar")
    ResponseEntity<?> iniciar(HttpSession sessao) {
        properties.exigirConfiguracaoCompleta();
        TenantId ente = resolvedorSessao.enteAutenticado(sessao);
        RepositorioSessaoAssinaturaGovBr.FluxoPendente fluxo =
                repositorio.iniciar(sessao, ente);
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
            HttpSession sessao) {
        if (state == null || state.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "state_ausente"));
        }
        RepositorioSessaoAssinaturaGovBr.FluxoPendente fluxo;
        try {
            fluxo = repositorio.consumirFluxo(sessao, state);
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
        AssinaturaGovBrOAuthToken token;
        try {
            token = clienteToken.trocarCodigoPorToken(code, fluxo.codeVerifier());
        } catch (IllegalStateException e) {
            throw new AssinaturaGovBrOAuthProvedorIndisponivelException(e);
        }
        ServicoIdentidade.Sessao sessaoIam =
                servicoIdentidade.autenticar(new ServicoIdentidade.CredencialGovBr(token.accessToken()));
        if (!sessaoIam.ente().equals(fluxo.ente())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("erro", "ente_divergente"));
        }
        sessoesIam.gravarVerificada(sessao, sessaoIam);
        repositorio.guardarToken(sessao, fluxo.ente(), token);
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
