package br.contabil.plataforma.infra.entrega;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxEntregaProperties.class)
public class OutboxEntregaConfiguration {

    @Bean
    @ConditionalOnMissingBean(BrokerEntrega.class)
    BrokerEntrega brokerEntregaIndisponivel() {
        return new BrokerEntregaIndisponivel();
    }
}
