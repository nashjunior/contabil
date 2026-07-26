package br.contabil.assinatura;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "siafic.assinatura.govbr.resilience")
record AssinaturaGovBrResilienceProperties(
        Duration connectTimeout,
        Duration requestTimeout,
        int retryMaxAttempts,
        Duration retryWaitDuration,
        int circuitSlidingWindowSize,
        int circuitMinimumNumberOfCalls,
        float circuitFailureRateThreshold,
        Duration circuitWaitDurationInOpenState,
        int circuitPermittedCallsInHalfOpenState) {

    private static final Duration CONNECT_TIMEOUT_PADRAO = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT_PADRAO = Duration.ofSeconds(10);
    private static final Duration RETRY_WAIT_DURATION_PADRAO = Duration.ofMillis(200);
    private static final Duration CIRCUIT_WAIT_OPEN_PADRAO = Duration.ofSeconds(30);

    AssinaturaGovBrResilienceProperties {
        connectTimeout = connectTimeout == null ? CONNECT_TIMEOUT_PADRAO : connectTimeout;
        requestTimeout = requestTimeout == null ? REQUEST_TIMEOUT_PADRAO : requestTimeout;
        retryMaxAttempts = retryMaxAttempts == 0 ? 2 : retryMaxAttempts;
        retryWaitDuration = retryWaitDuration == null ? RETRY_WAIT_DURATION_PADRAO : retryWaitDuration;
        circuitSlidingWindowSize = circuitSlidingWindowSize == 0 ? 10 : circuitSlidingWindowSize;
        circuitMinimumNumberOfCalls = circuitMinimumNumberOfCalls == 0 ? 5 : circuitMinimumNumberOfCalls;
        circuitFailureRateThreshold = circuitFailureRateThreshold == 0 ? 50.0F : circuitFailureRateThreshold;
        circuitWaitDurationInOpenState =
                circuitWaitDurationInOpenState == null ? CIRCUIT_WAIT_OPEN_PADRAO : circuitWaitDurationInOpenState;
        circuitPermittedCallsInHalfOpenState =
                circuitPermittedCallsInHalfOpenState == 0 ? 2 : circuitPermittedCallsInHalfOpenState;

        exigirPositivo(connectTimeout, "connect-timeout");
        exigirPositivo(requestTimeout, "request-timeout");
        exigirPositivo(retryWaitDuration, "retry-wait-duration");
        exigirPositivo(circuitWaitDurationInOpenState, "circuit-wait-duration-in-open-state");
        if (retryMaxAttempts < 1) {
            throw new IllegalArgumentException("retry-max-attempts deve ser >= 1");
        }
        if (circuitSlidingWindowSize < 1) {
            throw new IllegalArgumentException("circuit-sliding-window-size deve ser >= 1");
        }
        if (circuitMinimumNumberOfCalls < 1 || circuitMinimumNumberOfCalls > circuitSlidingWindowSize) {
            throw new IllegalArgumentException(
                    "circuit-minimum-number-of-calls deve estar entre 1 e circuit-sliding-window-size");
        }
        if (circuitFailureRateThreshold <= 0 || circuitFailureRateThreshold > 100) {
            throw new IllegalArgumentException("circuit-failure-rate-threshold deve estar entre 0 e 100");
        }
        if (circuitPermittedCallsInHalfOpenState < 1) {
            throw new IllegalArgumentException("circuit-permitted-calls-in-half-open-state deve ser >= 1");
        }
    }

    private static void exigirPositivo(Duration valor, String nome) {
        if (valor.isNegative() || valor.isZero()) {
            throw new IllegalArgumentException(nome + " deve ser positivo");
        }
    }
}
