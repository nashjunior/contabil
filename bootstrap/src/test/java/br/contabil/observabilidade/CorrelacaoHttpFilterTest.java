package br.contabil.observabilidade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import br.contabil.plataforma.infra.observabilidade.CorrelacaoIds;

class CorrelacaoHttpFilterTest {

    private final CorrelacaoHttpFilter filtro = new CorrelacaoHttpFilter();

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    @Test
    void geraCorrelationIdQuandoCabecalhoNaoVemDoChamador() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/entes/123/razao/saldo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> correlationIdNoMdc = new AtomicReference<>();

        filtro.doFilter(request, response, (servletRequest, servletResponse) ->
                correlationIdNoMdc.set(MDC.get(CorrelacaoIds.MDC_CORRELATION_ID)));

        assertThat(response.getHeader(CorrelacaoIds.CABECALHO)).isNotBlank();
        assertThat(correlationIdNoMdc.get()).isEqualTo(response.getHeader(CorrelacaoIds.CABECALHO));
        assertThat(MDC.get(CorrelacaoIds.MDC_CORRELATION_ID)).isNull();
    }

    @Test
    void propagaCorrelationIdValidoDoChamador() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/entes/123/execucao/empenhos");
        request.addHeader(CorrelacaoIds.CABECALHO, "portal-abc_123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> correlationIdNoMdc = new AtomicReference<>();

        filtro.doFilter(request, response, (servletRequest, servletResponse) ->
                correlationIdNoMdc.set(MDC.get(CorrelacaoIds.MDC_CORRELATION_ID)));

        assertThat(response.getHeader(CorrelacaoIds.CABECALHO)).isEqualTo("portal-abc_123");
        assertThat(correlationIdNoMdc.get()).isEqualTo("portal-abc_123");
        assertThat(MDC.get(CorrelacaoIds.MDC_CORRELATION_ID)).isNull();
    }

    @Test
    void substituiCorrelationIdInseguroParaEvitarInjecaoEmLog() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/entes/123/razao/balancete");
        request.addHeader(CorrelacaoIds.CABECALHO, "valor\ninvalido");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filtro.doFilter(request, response, (servletRequest, servletResponse) -> {});

        assertThat(response.getHeader(CorrelacaoIds.CABECALHO)).isNotEqualTo("valor\ninvalido");
        assertThat(response.getHeader(CorrelacaoIds.CABECALHO)).isNotBlank();
        assertThat(MDC.get(CorrelacaoIds.MDC_CORRELATION_ID)).isNull();
    }
}
