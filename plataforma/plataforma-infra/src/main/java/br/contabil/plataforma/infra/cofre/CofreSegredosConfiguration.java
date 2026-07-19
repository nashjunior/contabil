package br.contabil.plataforma.infra.cofre;

import br.contabil.plataforma.domain.cofre.CofreSegredos;
import br.contabil.plataforma.domain.cofre.CriptografiaDadosSensiveis;
import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CofreSegredosConfiguration {

    @Bean
    @ConditionalOnMissingBean(CofreSegredos.class)
    CofreSegredos cofreSegredos() {
        return CofreSegredosVariaveisAmbiente.sistema();
    }

    @Bean
    @ConditionalOnMissingBean(CriptografiaDadosSensiveis.class)
    CriptografiaDadosSensiveis criptografiaDadosSensiveis(
            CofreSegredos cofreSegredos,
            Clock clock,
            @Value("${siafic.seguranca.criptografia.conta-servico:siafic-crypto}") String contaServico,
            @Value("${siafic.seguranca.criptografia.escopos:cofre://siafic/f0/kms}") String escopos) {
        return new AesGcmCriptografiaDadosSensiveis(
                cofreSegredos,
                new CofreSegredos.ContaServico(contaServico, normalizarEscopos(escopos)),
                clock);
    }

    private static Set<String> normalizarEscopos(String escopos) {
        return Arrays.stream(escopos.split(","))
                .map(String::trim)
                .filter(escopo -> !escopo.isBlank())
                .collect(Collectors.toSet());
    }
}
