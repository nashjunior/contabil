package br.contabil.consulta;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.CredencialGovBr;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.NaoAutenticadoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.sessao.RepositorioAssercaoSessaoLoginGovBr;

@ExtendWith(MockitoExtension.class)
class SessaoAutenticadaHttpResolverTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    private final RepositorioAssercaoSessaoLoginGovBr repositorioAssercaoSessao = new RepositorioAssercaoSessaoLoginGovBr();

    private SessaoAutenticadaHttpResolver resolver;

    private SessaoAutenticadaHttpResolver resolver() {
        return resolver != null
                ? resolver
                : (resolver = new SessaoAutenticadaHttpResolver(servicoIdentidade, repositorioAssercaoSessao));
    }

    @Test
    void suportaApenasParametroDoTipoSessao() throws NoSuchMethodException {
        var comSessao = getClass().getDeclaredMethod("assinaturaComSessao", Sessao.class);
        var semSessao = getClass().getDeclaredMethod("assinaturaSemSessao", String.class);

        assertThat(resolver().supportsParameter(new org.springframework.core.MethodParameter(comSessao, 0)))
                .isTrue();
        assertThat(resolver().supportsParameter(new org.springframework.core.MethodParameter(semSessao, 0)))
                .isFalse();
    }

    @Test
    void semCabecalhoAutorizacaoFalhaFechado() {
        ServletWebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest());

        assertThatThrownBy(() -> resolver().autenticar(webRequest))
                .isInstanceOf(NaoAutenticadoException.class);
    }

    @Test
    void cabecalhoSemPrefixoBearerFalhaFechado() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        assertThatThrownBy(() -> resolver().autenticar(webRequest))
                .isInstanceOf(NaoAutenticadoException.class);
    }

    @Test
    void bearerVazioFalhaFechado() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer    ");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        assertThatThrownBy(() -> resolver().autenticar(webRequest))
                .isInstanceOf(NaoAutenticadoException.class);
    }

    @Test
    void bearerValidoAutenticaViaServicoIdentidadeEDevolveSessao() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer assercao-govbr-valida");
        ServletWebRequest webRequest = new ServletWebRequest(request);
        Sessao esperada = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                new TenantId(UUID.randomUUID()),
                Optional.empty(),
                false,
                Instant.parse("2030-01-01T00:00:00Z"));
        when(servicoIdentidade.autenticar(new CredencialGovBr("assercao-govbr-valida"))).thenReturn(esperada);

        Sessao sessao = resolver().autenticar(webRequest);

        assertThat(sessao).isSameAs(esperada);
    }

    @Test
    void propagaMfaRequeridoDoServicoIdentidade() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer assercao-parcial");
        ServletWebRequest webRequest = new ServletWebRequest(request);
        when(servicoIdentidade.autenticar(new CredencialGovBr("assercao-parcial")))
                .thenThrow(new MfaRequeridoException(new ServicoIdentidade.DesafioMfa(UUID.randomUUID(), "canal")));

        assertThatThrownBy(() -> resolver().autenticar(webRequest)).isInstanceOf(MfaRequeridoException.class);
    }

    @Test
    void semCabecalhoMasComCookieDeSessaoValidoReverificaViaServicoIdentidadeACadaChamada() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new org.springframework.mock.web.MockHttpSession());
        repositorioAssercaoSessao.gravarAposLogin(request, "assercao-govbr-da-sessao-cookie");
        ServletWebRequest webRequest = new ServletWebRequest(request);
        Sessao esperada = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                new TenantId(UUID.randomUUID()),
                Optional.empty(),
                true,
                Instant.parse("2030-01-01T00:00:00Z"));
        when(servicoIdentidade.autenticar(new CredencialGovBr("assercao-govbr-da-sessao-cookie"))).thenReturn(esperada);

        Sessao sessao = resolver().autenticar(webRequest);

        assertThat(sessao).isSameAs(esperada);
    }

    @Test
    void semCabecalhoESemCookieDeSessaoFalhaFechado() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new org.springframework.mock.web.MockHttpSession());
        ServletWebRequest webRequest = new ServletWebRequest(request);

        assertThatThrownBy(() -> resolver().autenticar(webRequest)).isInstanceOf(NaoAutenticadoException.class);
    }

    @Test
    void cabecalhoTemPrioridadeSobreCookieDeSessaoQuandoAmbosPresentes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new org.springframework.mock.web.MockHttpSession());
        repositorioAssercaoSessao.gravarAposLogin(request, "assercao-da-sessao-cookie");
        request.addHeader("Authorization", "Bearer assercao-do-cabecalho");
        ServletWebRequest webRequest = new ServletWebRequest(request);
        Sessao esperada = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                new TenantId(UUID.randomUUID()),
                Optional.empty(),
                true,
                Instant.parse("2030-01-01T00:00:00Z"));
        when(servicoIdentidade.autenticar(new CredencialGovBr("assercao-do-cabecalho"))).thenReturn(esperada);

        Sessao sessao = resolver().autenticar(webRequest);

        assertThat(sessao).isSameAs(esperada);
    }

    @SuppressWarnings("unused")
    private void assinaturaComSessao(Sessao sessao) {}

    @SuppressWarnings("unused")
    private void assinaturaSemSessao(String texto) {}
}
