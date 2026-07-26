package br.contabil.plataforma.infra.cofre;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.contabil.plataforma.domain.cofre.CofreSegredos;
import br.contabil.plataforma.domain.cofre.CriptografiaDadosSensiveis;

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
            @Value("${siafic.seguranca.criptografia.escopos:cofre://siafic/f0/criptografia}") String escopos) {
        return new AesGcmCriptografiaDadosSensiveis(
                cofreSegredos,
                new CofreSegredos.ContaServico(contaServico, normalizarEscopos(escopos)),
                clock);
    }

    private static Set<String> normalizarEscopos(String escopos) {
        Set<String> resultado = new HashSet<>();
        for (String parte : escopos.split(",")) {
            String escopo = parte.trim();
            if (!escopo.isBlank()) {
                resultado.add(escopo);
            }
        }
        return resultado;
    }
}
