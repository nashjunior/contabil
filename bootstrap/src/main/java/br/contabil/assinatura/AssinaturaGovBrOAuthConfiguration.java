package br.contabil.assinatura;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.contabil.plataforma.infra.assinatura.ProvedorAssinaturaGovBr.ContaGovBrNaoElegivelException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.function.Supplier;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AssinaturaGovBrOAuthProperties.class, AssinaturaGovBrResilienceProperties.class})
class AssinaturaGovBrOAuthConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AssinaturaGovBrOAuthConfiguration.class);

    @Bean
    HttpClient assinaturaGovBrHttpClient(AssinaturaGovBrResilienceProperties resilienceProperties) {
        return HttpClient.newBuilder()
                .connectTimeout(resilienceProperties.connectTimeout())
                .build();
    }

    @Bean
    CircuitBreaker assinaturaGovBrCircuitBreaker(AssinaturaGovBrResilienceProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(properties.circuitSlidingWindowSize())
                .minimumNumberOfCalls(properties.circuitMinimumNumberOfCalls())
                .failureRateThreshold(properties.circuitFailureRateThreshold())
                .waitDurationInOpenState(properties.circuitWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.circuitPermittedCallsInHalfOpenState())
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("assinatura-govbr", config);
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                LOG.warn("event=resilience.circuit_breaker.open name=assinatura-govbr transition={}",
                        event.getStateTransition());
            } else {
                LOG.info("event=resilience.circuit_breaker.transition name=assinatura-govbr transition={}",
                        event.getStateTransition());
            }
        });
        return circuitBreaker;
    }

    @Bean
    MeterBinder assinaturaGovBrCircuitBreakerMeterBinder(CircuitBreaker assinaturaGovBrCircuitBreaker) {
        return registry -> Gauge.builder(
                        "siafic_assinatura_govbr_circuit_breaker_state",
                        assinaturaGovBrCircuitBreaker,
                        AssinaturaGovBrOAuthConfiguration::estadoCircuitBreaker)
                .description("Estado do circuit breaker de assinatura gov.br: closed=0, half_open=1, open=2")
                .tag("name", assinaturaGovBrCircuitBreaker.getName())
                .register(registry);
    }

    @Bean
    Retry assinaturaGovBrRetry(AssinaturaGovBrResilienceProperties properties) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.retryMaxAttempts())
                .intervalFunction(IntervalFunction.of(properties.retryWaitDuration()))
                .ignoreExceptions(CallNotPermittedException.class, ContaGovBrNaoElegivelException.class)
                .build();
        return Retry.of("assinatura-govbr", config);
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
            CircuitBreaker assinaturaGovBrCircuitBreaker,
            ObjectMapper objectMapper,
            Clock clock,
            AssinaturaGovBrOAuthProperties properties,
            AssinaturaGovBrResilienceProperties resilienceProperties) {
        return new AssinaturaGovBrOAuthClient(
                assinaturaGovBrHttpClient,
                objectMapper,
                clock,
                properties,
                resilienceProperties.requestTimeout(),
                assinaturaGovBrCircuitBreaker);
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

    private static double estadoCircuitBreaker(CircuitBreaker circuitBreaker) {
        return switch (circuitBreaker.getState()) {
            case CLOSED -> 0.0;
            case HALF_OPEN -> 1.0;
            case OPEN, FORCED_OPEN -> 2.0;
            case DISABLED, METRICS_ONLY -> -1.0;
        };
    }
}
