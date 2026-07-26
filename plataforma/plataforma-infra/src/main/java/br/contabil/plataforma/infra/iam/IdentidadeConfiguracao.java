package br.contabil.plataforma.infra.iam;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;

/**
 * Wiring de IAM (ADR-0002 / AGENTS.md): {@link ServicoIdentidade} é o adapter
 * gov.br/ICP-Brasil (RAZ-5) quando {@code siafic.iam.enabled=true}; sem
 * configuração explícita fica fail-closed via {@link ServicoIdentidadeIndisponivel}.
 * {@link ControleAcesso} é o serviço de domínio (RAZ-33) que os use cases
 * protegidos consomem.
 */
@Configuration
@EnableConfigurationProperties(IamProperties.class)
public class IdentidadeConfiguracao {

    @Bean("servicoIdentidade")
    @ConditionalOnProperty(prefix = "siafic.iam", name = "enabled", havingValue = "true")
    ServicoIdentidade servicoIdentidadeGovBrIcp(
            IamProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        properties.validar();
        return new ServicoIdentidadeGovBrIcp(
                new VerificadorJwtGovBr(objectMapper, clock, properties.govbr()),
                new VerificadorCertificadoIcp(clock, properties.icp()),
                properties,
                clock);
    }

    @Bean("servicoIdentidade")
    @ConditionalOnProperty(prefix = "siafic.iam", name = "enabled", havingValue = "false", matchIfMissing = true)
    ServicoIdentidade servicoIdentidadeFailClosed() {
        return new ServicoIdentidadeIndisponivel();
    }

    @Bean
    ControleAcesso controleAcesso(ServicoIdentidade servicoIdentidade) {
        return new ControleAcesso(servicoIdentidade);
    }
}
