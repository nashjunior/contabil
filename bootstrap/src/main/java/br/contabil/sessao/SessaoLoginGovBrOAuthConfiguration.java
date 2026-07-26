package br.contabil.sessao;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wiring do BFF de login OIDC gov.br (ADR-0035) — sempre ativo (ao contrário da
 * composição de assinatura, {@code ServicoAssinaturaGovBrConfiguration}, que é
 * {@code @ConditionalOnProperty}): sem credenciais no cofre/ambiente, o próprio
 * {@link SessaoLoginGovBrOAuthProperties#exigirConfiguracaoCompleta()} falha fechado
 * só quando o fluxo é acionado, preservando o startup local (mesma postura do
 * ADR-0017).
 */
@Configuration
@EnableConfigurationProperties(SessaoLoginGovBrOAuthProperties.class)
class SessaoLoginGovBrOAuthConfiguration {

    @Bean
    HttpClient loginGovBrHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    SecureRandom loginGovBrSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    RepositorioFluxoLoginGovBr repositorioFluxoLoginGovBr(
            SecureRandom loginGovBrSecureRandom, Clock clock, SessaoLoginGovBrOAuthProperties properties) {
        return new RepositorioFluxoLoginGovBr(loginGovBrSecureRandom, clock, properties);
    }

    @Bean
    ClienteTokenSessaoLoginGovBr clienteTokenSessaoLoginGovBr(
            HttpClient loginGovBrHttpClient,
            ObjectMapper objectMapper,
            Clock clock,
            SessaoLoginGovBrOAuthProperties properties) {
        return new SessaoLoginGovBrOAuthClient(loginGovBrHttpClient, objectMapper, clock, properties);
    }

    @Bean
    RepositorioAssercaoSessaoLoginGovBr repositorioAssercaoSessaoLoginGovBr() {
        return new RepositorioAssercaoSessaoLoginGovBr();
    }

    @Bean
    FilterRegistrationBean<CsrfCookieProtecaoFilter> csrfCookieProtecaoFilter(
            RepositorioAssercaoSessaoLoginGovBr repositorioAssercaoSessaoLoginGovBr, ObjectMapper objectMapper) {
        var registration = new FilterRegistrationBean<>(
                new CsrfCookieProtecaoFilter(repositorioAssercaoSessaoLoginGovBr, objectMapper));
        registration.addUrlPatterns("/*");
        return registration;
    }
}
