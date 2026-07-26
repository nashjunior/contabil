package br.contabil.consulta;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.contabil.plataforma.domain.iam.ServicoIdentidade;

/**
 * Registra {@link SessaoAutenticadaHttpResolver} para todo {@code @RestController}
 * do sistema — primeiro precedente de camada REST de consulta (RAZ-101).
 */
@Configuration(proxyBeanMethods = false)
class ConsultaWebConfiguration implements WebMvcConfigurer {

    private final ServicoIdentidade servicoIdentidade;

    ConsultaWebConfiguration(ServicoIdentidade servicoIdentidade) {
        this.servicoIdentidade = servicoIdentidade;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new SessaoAutenticadaHttpResolver(servicoIdentidade));
    }
}
