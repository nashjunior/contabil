package br.contabil.sessao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class RepositorioAssercaoSessaoLoginGovBrTest {

    private final RepositorioAssercaoSessaoLoginGovBr repositorio = new RepositorioAssercaoSessaoLoginGovBr();

    @Test
    void gravarAposLoginRegeneraIdDaSessaoAntesDeGuardarAAssercao() {
        MockHttpSession sessaoOriginal = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessaoOriginal);
        String idOriginal = sessaoOriginal.getId();

        repositorio.gravarAposLogin(request, "assercao-jwt-govbr");

        assertThat(request.getSession(false).getId()).isNotEqualTo(idOriginal);
        assertThat(repositorio.assercaoDaSessao(request.getSession(false))).contains("assercao-jwt-govbr");
    }

    @Test
    void assercaoNaoPresenteDevolveOptionalVazio() {
        MockHttpSession sessao = new MockHttpSession();

        assertThat(repositorio.assercaoDaSessao(sessao)).isEmpty();
    }

    @Test
    void sessaoNulaDevolveOptionalVazioSemLancar() {
        assertThat(repositorio.assercaoDaSessao(null)).isEmpty();
    }

    @Test
    void assercaoEmBrancoNaoPodeSerGravada() {
        MockHttpSession sessao = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);

        assertThatThrownBy(() -> repositorio.gravarAposLogin(request, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encerrarInvalidaSessaoEDescartaAssercaoGuardada() {
        MockHttpSession sessao = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessao);
        repositorio.gravarAposLogin(request, "assercao-jwt-govbr");
        MockHttpSession sessaoRegenerada = (MockHttpSession) request.getSession(false);

        repositorio.encerrar(sessaoRegenerada);

        assertThat(sessaoRegenerada.isInvalid()).isTrue();
    }

    @Test
    void encerrarComSessaoNulaNaoLanca() {
        repositorio.encerrar(null);
    }
}
