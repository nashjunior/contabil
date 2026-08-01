package br.contabil.sessao;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wiring do IdP de dev local (RAZ-228/ADR-0052 item 1) — ativo só quando AMBAS as flags
 * de guarda estão ligadas: {@code siafic.dev-idp.enabled} (a flag explícita e exclusiva
 * deste endpoint) E {@code siafic.iam.enabled} (o verificador real, {@code
 * VerificadorJwtGovBr}, precisa estar de pé para os bearers emitidos aqui servirem pra
 * alguma coisa — defesa em profundidade adicional às duas flags do ADR-0052), ver {@link
 * ConditionalOnDevIdpAtivo}. Sem as duas, nem {@link AssinadorJwtDevIdp} nem {@link
 * SessaoDevIdpController} existem como bean — a rota HTTP correspondente literalmente não
 * é registrada (404, não 403/500), o mesmo padrão fail-closed-por-ausência de {@code
 * IdentidadeConfiguracao}.
 */
@Configuration
@EnableConfigurationProperties(SessaoDevIdpProperties.class)
@ConditionalOnDevIdpAtivo
class SessaoDevIdpConfiguration {

    @Bean
    AssinadorJwtDevIdp assinadorJwtDevIdp(ObjectMapper objectMapper, Clock clock, SessaoDevIdpProperties properties) {
        return new AssinadorJwtDevIdp(objectMapper, clock, properties);
    }
}
