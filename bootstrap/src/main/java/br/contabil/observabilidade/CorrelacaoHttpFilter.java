package br.contabil.observabilidade;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import br.contabil.plataforma.infra.observabilidade.CorrelacaoIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class CorrelacaoHttpFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(CorrelacaoHttpFilter.class);

    private static final String MDC_HTTP_METHOD = "httpMethod";
    private static final String MDC_HTTP_PATH = "httpPath";
    private static final String MDC_HTTP_STATUS = "httpStatus";
    private static final String MDC_HTTP_DURATION_MS = "httpDurationMs";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String correlationId = CorrelacaoIds.resolver(request.getHeader(CorrelacaoIds.CABECALHO));
        long inicio = System.nanoTime();
        MDC.put(CorrelacaoIds.MDC_CORRELATION_ID, correlationId);
        response.setHeader(CorrelacaoIds.CABECALHO, correlationId);
        boolean falha = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            falha = true;
            throw e;
        } finally {
            registrarFimDaRequisicao(request, response, inicio, falha);
            limparMdc();
        }
    }

    private static void registrarFimDaRequisicao(
            HttpServletRequest request, HttpServletResponse response, long inicioNanos, boolean falha) {
        int status = falha && response.getStatus() < 400 ? 500 : response.getStatus();
        MDC.put(MDC_HTTP_METHOD, request.getMethod());
        MDC.put(MDC_HTTP_PATH, request.getRequestURI());
        MDC.put(MDC_HTTP_STATUS, Integer.toString(status));
        MDC.put(MDC_HTTP_DURATION_MS, Long.toString(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNanos)));
        LOG.info("http_request");
    }

    private static void limparMdc() {
        MDC.remove(MDC_HTTP_DURATION_MS);
        MDC.remove(MDC_HTTP_STATUS);
        MDC.remove(MDC_HTTP_PATH);
        MDC.remove(MDC_HTTP_METHOD);
        MDC.remove(CorrelacaoIds.MDC_CORRELATION_ID);
    }
}
