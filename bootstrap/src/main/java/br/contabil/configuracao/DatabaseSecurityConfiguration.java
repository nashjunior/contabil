package br.contabil.configuracao;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class DatabaseSecurityConfiguration {

    @Bean
    InitializingBean validarConfiguracaoSeguraDoBanco(Environment environment) {
        return () -> DatabaseSecuritySettings.from(environment).validar();
    }
}
