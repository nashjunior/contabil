package br.contabil.plataforma.infra.transparencia;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.contabil.plataforma.application.ConsultarTransparenciaPublica;
import br.contabil.plataforma.application.TotalizarTransparenciaPublica;
import br.contabil.plataforma.domain.transparencia.TransparenciaPublicaQuery;
import br.contabil.plataforma.infra.entrega.BrokerEntrega;
import io.micrometer.core.instrument.MeterRegistry;

/** Wiring da transparência ativa pública (RAZ-273/ADR-0059). */
@Configuration
public class TransparenciaConfiguracao {

    @Bean
    ConsultarTransparenciaPublica consultarTransparenciaPublica(TransparenciaPublicaQuery query) {
        return new ConsultarTransparenciaPublica(query);
    }

    @Bean
    TotalizarTransparenciaPublica totalizarTransparenciaPublica(TransparenciaPublicaQuery query) {
        return new TotalizarTransparenciaPublica(query);
    }

    @Bean
    BrokerEntrega brokerEntregaTransparencia(
            JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock, MeterRegistry meterRegistry) {
        return new PostgresTransparenciaBrokerEntrega(jdbcTemplate, objectMapper, clock, meterRegistry);
    }
}
