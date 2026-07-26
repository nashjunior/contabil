package br.contabil.sessao;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;

class CsrfCookieProtecaoFilterTest {

    private final RepositorioAssercaoSessaoLoginGovBr repositorioAssercao = new RepositorioAssercaoSessaoLoginGovBr();
    private final CsrfCookieProtecaoFilter filtro =
            new CsrfCookieProtecaoFilter(repositorioAssercao, new ObjectMapper());

    @Test
    void requisicaoMutanteAutenticadaPorCookieSemTokenCsrfFalhaFechado() throws Exception {
        MockHttpServletRequest request = requisicaoAutenticadaPorCookie("POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainFake chain = new FiltroChainFake();

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("csrf_invalido");
        assertThat(chain.chamado).isFalse();
    }

    @Test
    void requisicaoMutanteAutenticadaPorCookieComTokenCsrfDivergenteFalhaFechado() throws Exception {
        MockHttpServletRequest request = requisicaoAutenticadaPorCookie("POST");
        request.setCookies(new Cookie(CsrfCookieSessaoLogin.NOME_COOKIE, "valor-do-cookie"));
        request.addHeader(CsrfCookieSessaoLogin.CABECALHO_CSRF, "valor-diferente");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainFake chain = new FiltroChainFake();

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.chamado).isFalse();
    }

    @Test
    void requisicaoMutanteAutenticadaPorCookieComTokenCsrfCorretoPassa() throws Exception {
        MockHttpServletRequest request = requisicaoAutenticadaPorCookie("POST");
        request.setCookies(new Cookie(CsrfCookieSessaoLogin.NOME_COOKIE, "token-valido"));
        request.addHeader(CsrfCookieSessaoLogin.CABECALHO_CSRF, "token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainFake chain = new FiltroChainFake();

        filtro.doFilter(request, response, chain);

        assertThat(chain.chamado).isTrue();
    }

    @Test
    void requisicaoGetNaoExigeTokenCsrfMesmoAutenticadaPorCookie() throws Exception {
        MockHttpServletRequest request = requisicaoAutenticadaPorCookie("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainFake chain = new FiltroChainFake();

        filtro.doFilter(request, response, chain);

        assertThat(chain.chamado).isTrue();
    }

    @Test
    void requisicaoMutanteComCabecalhoAuthorizationNaoExigeTokenCsrf() throws Exception {
        MockHttpServletRequest request = requisicaoAutenticadaPorCookie("POST");
        request.addHeader("Authorization", "Bearer assercao-de-chamador-externo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainFake chain = new FiltroChainFake();

        filtro.doFilter(request, response, chain);

        assertThat(chain.chamado).isTrue();
    }

    @Test
    void requisicaoMutanteSemSessaoDeLoginNenhumaNaoExigeTokenCsrf() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/razao/qualquer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainFake chain = new FiltroChainFake();

        filtro.doFilter(request, response, chain);

        assertThat(chain.chamado).isTrue();
    }

    private MockHttpServletRequest requisicaoAutenticadaPorCookie(String metodo) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, "/razao/qualquer");
        request.setSession(new MockHttpSession());
        repositorioAssercao.gravarAposLogin(request, "assercao-govbr-da-sessao");
        return request;
    }

    private static final class FiltroChainFake implements FilterChain {
        private boolean chamado;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            chamado = true;
        }
    }
}
