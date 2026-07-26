package br.contabil.sessao;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Exige o token anti-CSRF (padrão <i>double-submit cookie</i>) em toda chamada mutante
 * autenticada pelo cookie de sessão do BFF de login — ausência ou divergência falha
 * fechado com {@code 403} (ADR-0035 §3, refinamento de segurança RAZ-127 §2).
 *
 * <p>Só se aplica quando a requisição <b>seria</b> autenticada pelo <i>fallback</i> de
 * cookie (sem cabeçalho {@code Authorization} e com sessão carregando asserção guardada,
 * {@link RepositorioAssercaoSessaoLoginGovBr}) — chamadores externos por cabeçalho
 * (RAZ-84) e requisições sem sessão de login nenhuma (que o resolver já rejeita com
 * {@code 401}) não passam por este filtro.
 */
final class CsrfCookieProtecaoFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_MUTANTES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String CABECALHO_AUTORIZACAO = "Authorization";

    private final RepositorioAssercaoSessaoLoginGovBr repositorioAssercao;
    private final ObjectMapper objectMapper;

    CsrfCookieProtecaoFilter(RepositorioAssercaoSessaoLoginGovBr repositorioAssercao, ObjectMapper objectMapper) {
        this.repositorioAssercao = Objects.requireNonNull(repositorioAssercao, "repositorioAssercao");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (exigeCsrf(request) && !tokenCsrfValido(request)) {
            responderCsrfInvalido(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean exigeCsrf(HttpServletRequest request) {
        if (!METODOS_MUTANTES.contains(request.getMethod())) {
            return false;
        }
        String autorizacao = request.getHeader(CABECALHO_AUTORIZACAO);
        if (autorizacao != null && !autorizacao.isBlank()) {
            return false;
        }
        HttpSession sessao = request.getSession(false);
        return repositorioAssercao.assercaoDaSessao(sessao).isPresent();
    }

    private boolean tokenCsrfValido(HttpServletRequest request) {
        String doCabecalho = request.getHeader(CsrfCookieSessaoLogin.CABECALHO_CSRF);
        String doCookie = valorCookie(request, CsrfCookieSessaoLogin.NOME_COOKIE);
        return doCookie != null && !doCookie.isBlank() && doCookie.equals(doCabecalho);
    }

    private static String valorCookie(HttpServletRequest request, String nome) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (nome.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void responderCsrfInvalido(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                Map.of(
                        "codigo", "csrf_invalido",
                        "mensagem", "Token anti-CSRF ausente ou divergente do cookie " + CsrfCookieSessaoLogin.NOME_COOKIE,
                        "detalhes", Map.of()));
    }
}
