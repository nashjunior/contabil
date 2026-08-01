package br.contabil.configuracao;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para quando o SPA e o backend não compartilham origem (ex.: RAZ-211 — suíte
 * E2E de navegador rodando o SPA num host/porta diferente da API real). Desligado
 * por padrão: sem {@code siafic.cors.allowed-origins} configurado, nenhuma origem é
 * registrada e o comportamento é idêntico ao de hoje (sem cabeçalhos CORS).
 *
 * <p>{@code allowedOrigins} nunca aceita {@code "*"} — as chamadas mutantes do client
 * usam {@code credentials: 'include'} (cookie de sessão do BFF, ADR-0035), e CORS com
 * credenciais exige origem explícita por especificação.
 */
@Configuration(proxyBeanMethods = false)
class CorsWebConfiguration implements WebMvcConfigurer {

    private final List<String> origensPermitidas;

    CorsWebConfiguration(Environment environment) {
        String bruto = environment.getProperty("siafic.cors.allowed-origins", "");
        this.origensPermitidas = bruto.isBlank()
                ? List.of()
                : Arrays.stream(bruto.split(","))
                        .map(String::trim)
                        .filter(origem -> !origem.isBlank())
                        .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (origensPermitidas.isEmpty()) {
            return;
        }
        registry.addMapping("/**")
                .allowedOrigins(origensPermitidas.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
