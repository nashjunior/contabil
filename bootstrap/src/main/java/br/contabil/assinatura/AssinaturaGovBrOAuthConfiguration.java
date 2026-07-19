package br.contabil.assinatura;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AssinaturaGovBrOAuthProperties.class)
class AssinaturaGovBrOAuthConfiguration {

    @Bean
    HttpClient assinaturaGovBrHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    RepositorioSessaoAssinaturaGovBr repositorioSessaoAssinaturaGovBr(
            Clock clock,
            AssinaturaGovBrOAuthProperties properties) {
        return new RepositorioSessaoAssinaturaGovBr(new SecureRandom(), clock, properties);
    }

    @Bean
    ClienteTokenAssinaturaGovBr clienteTokenAssinaturaGovBr(
            HttpClient assinaturaGovBrHttpClient,
            ObjectMapper objectMapper,
            Clock clock,
            AssinaturaGovBrOAuthProperties properties) {
        return new AssinaturaGovBrOAuthClient(assinaturaGovBrHttpClient, objectMapper, clock, properties);
    }

    @Bean
    Supplier<String> tokenAcessoAssinaturaGovBr(RepositorioSessaoAssinaturaGovBr repositorio) {
        return new AssinaturaGovBrTokenSessaoSupplier(repositorio);
    }

    @Bean
    SessaoIamAssinaturaHttpSession sessaoIamAssinaturaHttpSession(Clock clock) {
        return new SessaoIamAssinaturaHttpSession(clock);
    }

    @Bean
    ResolvedorSessaoAssinaturaGovBr resolvedorSessaoAssinaturaGovBr(SessaoIamAssinaturaHttpSession sessoesIam) {
        return new ResolvedorSessaoAssinaturaGovBrHttpSession(sessoesIam);
    }
}
