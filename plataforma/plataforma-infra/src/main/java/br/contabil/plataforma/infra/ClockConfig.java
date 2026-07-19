package br.contabil.plataforma.infra;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único do sistema — SEMPRE o do servidor (UTC), nunca de input do
 * cliente (anti-backdating, Regra 2, 05-regras-de-negocio.md). Ponto único de
 * composição do {@link Clock} injetado nos use cases do razão e demais módulos.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
