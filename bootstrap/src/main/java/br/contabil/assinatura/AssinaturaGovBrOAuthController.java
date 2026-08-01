package br.contabil.assinatura;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import br.contabil.plataforma.infra.observabilidade.CorrelacaoIds;
import jakarta.servlet.http.HttpSession;

/**
 * O callback do OAuth2 gov.br é aberto pelo navegador do operador, não por um
 * cliente {@code fetch} — por isso responde sempre com {@code 302} para a rota
 * fixa do SPA ({@code frontend-retorno-uri}, ADR-0039 decisão 3), nunca com corpo
 * JSON cru: o navegador do gov.br não tem onde renderizar isso. {@code resultado}
 * e {@code codigo} (taxonomia {@code ErroContrato} §6.1) são os únicos dados na
 * query string — nenhum material sensível (token, CPF, hash) trafega ali (R4 da
 * ratificação RAZ-152).
 */
@RestController
@RequestMapping("/assinatura/oauth")
final class AssinaturaGovBrOAuthController {

    private static final Logger LOG = LoggerFactory.getLogger(AssinaturaGovBrOAuthController.class);

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
        properties.exigirConfiguracaoCompleta();
        if (state == null || state.isBlank()) {
            return redirecionarParaFrontend("state_ausente");
        }
        RepositorioSessaoAssinaturaGovBr.FluxoPendente fluxo;
        try {
            fluxo = repositorio.consumirFluxo(sessao, state);
        } catch (IllegalStateException e) {
            return redirecionarParaFrontend("state_invalido");
        }
        if (error != null && !error.isBlank()) {
            LOG.info("gov.br recusou OAuth2 de assinatura: error={} error_description={}", error, errorDescription);
            return redirecionarParaFrontend("oauth_recusado");
        }
        if (code == null || code.isBlank()) {
            return redirecionarParaFrontend("code_ausente");
        }
        AssinaturaGovBrOAuthToken token;
        try {
            token = clienteToken.trocarCodigoPorToken(code, fluxo.codeVerifier());
        } catch (IllegalStateException e) {
            AssinaturaGovBrOAuthProvedorIndisponivelException falha = new AssinaturaGovBrOAuthProvedorIndisponivelException(e);
            String correlationId = CorrelacaoIds.atualOuNovo();
            LOG.error("Falha ao trocar code OAuth2 por token gov.br [correlationId={}]", correlationId, falha);
            return redirecionarParaFrontend(falha.codigo());
        }
        ServicoIdentidade.Sessao sessaoIam =
                servicoIdentidade.autenticar(new ServicoIdentidade.CredencialGovBr(token.accessToken()));
        if (!sessaoIam.ente().equals(fluxo.ente())) {
            return redirecionarParaFrontend("ente_divergente");
        }
        sessoesIam.gravarVerificada(sessao, sessaoIam);
        repositorio.guardarToken(sessao, fluxo.ente(), token);
        return redirecionarParaFrontendComSucesso();
    }

    private ResponseEntity<?> redirecionarParaFrontendComSucesso() {
        URI destino = UriComponentsBuilder.fromUri(
                        Objects.requireNonNull(properties.frontendRetornoUri(), "frontendRetornoUri"))
                .queryParam("resultado", "ok")
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, destino.toString()).build();
    }

    /** {@code codigo} é sempre a taxonomia estável do §6.1 — nunca texto livre vindo do gov.br (R4 da ratificação RAZ-152). */
    private ResponseEntity<?> redirecionarParaFrontend(String codigo) {
        URI destino = UriComponentsBuilder.fromUri(
                        Objects.requireNonNull(properties.frontendRetornoUri(), "frontendRetornoUri"))
                .queryParam("resultado", "erro")
                .queryParam("codigo", codigo)
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, destino.toString()).build();
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
}
