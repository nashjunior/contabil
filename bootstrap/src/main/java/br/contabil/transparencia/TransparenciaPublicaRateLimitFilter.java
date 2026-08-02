package br.contabil.transparencia;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Quota pública simples para a borda sem cadastro da transparência ativa. */
@Component
final class TransparenciaPublicaRateLimitFilter extends OncePerRequestFilter {

    private static final int LIMITE_POR_MINUTO = 120;
    private static final long JANELA_MILLIS = 60_000L;

    private final Clock clock;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    TransparenciaPublicaRateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/transparencia/")) {
            filterChain.doFilter(request, response);
            return;
        }

        long agora = clock.millis();
        Janela janela = janelas.compute(chave(request), (chave, atual) -> {
            if (atual == null || agora >= atual.inicioMillis + JANELA_MILLIS) {
                return new Janela(agora, new AtomicInteger(1));
            }
            atual.contador.incrementAndGet();
            return atual;
        });

        response.setHeader("X-RateLimit-Limit", Integer.toString(LIMITE_POR_MINUTO));
        response.setHeader("X-RateLimit-Window-Seconds", "60");
        if (janela.contador.get() > LIMITE_POR_MINUTO) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"codigo\":\"quota_transparencia_excedida\",\"mensagem\":\"quota pública excedida\",\"detalhes\":{}}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String chave(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",", 2)[0].trim();
        return ip + "|" + request.getRequestURI();
    }

    private record Janela(long inicioMillis, AtomicInteger contador) {}
}
