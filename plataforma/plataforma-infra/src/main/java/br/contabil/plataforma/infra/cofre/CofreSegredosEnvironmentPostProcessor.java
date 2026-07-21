package br.contabil.plataforma.infra.cofre;

import br.contabil.plataforma.domain.cofre.CofreSegredos;
import br.contabil.plataforma.domain.cofre.CofreSegredos.ContaServico;
import br.contabil.plataforma.domain.cofre.CofreSegredos.ReferenciaSegredo;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Resolve referencias logicas de segredo antes do auto-wiring do Spring Boot.
 * Assim DataSource/Flyway/OAuth recebem valores efetivos sem hardcode em YAML.
 */
public final class CofreSegredosEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE = "siafic-cofre-segredos-resolvidos";
    private static final Map<String, String> SEGREDOS_RUNTIME = Map.of(
            "spring.datasource.password-ref", "spring.datasource.password",
            "spring.flyway.password-ref", "spring.flyway.password",
            "siafic.assinatura.govbr.oauth.client-secret-ref", "siafic.assinatura.govbr.oauth.client-secret");

    private final CofreSegredos cofre;

    public CofreSegredosEnvironmentPostProcessor() {
        this(CofreSegredosVariaveisAmbiente.sistema());
    }

    CofreSegredosEnvironmentPostProcessor(CofreSegredos cofre) {
        this.cofre = cofre;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ContaServico conta = contaServico(environment);
        Map<String, Object> resolvidos = new LinkedHashMap<>();

        SEGREDOS_RUNTIME.forEach((referenciaProperty, destinoProperty) -> {
            String referencia = environment.getProperty(referenciaProperty);
            if (referencia == null || referencia.isBlank()) {
                return;
            }
            String valorDireto = environment.getProperty(destinoProperty);
            if (valorDireto != null && !valorDireto.isBlank()) {
                throw new IllegalStateException(destinoProperty
                        + " nao pode coexistir com " + referenciaProperty
                        + "; segredos runtime devem vir apenas do cofre");
            }
            char[] valor = cofre.obter(conta, new ReferenciaSegredo(referencia)).valor();
            try {
                resolvidos.put(destinoProperty, new String(valor));
            } finally {
                Arrays.fill(valor, '\0');
            }
        });

        if (!resolvidos.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, resolvidos));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private static ContaServico contaServico(ConfigurableEnvironment environment) {
        String nome = environment.getProperty("siafic.seguranca.cofre.conta-servico", "siafic-runtime");
        String escopos = environment.getProperty("siafic.seguranca.cofre.escopos", "cofre:*");
        Set<String> normalizados = new HashSet<>();
        for (String parte : escopos.split(",")) {
            String escopo = parte.trim();
            if (!escopo.isBlank()) {
                normalizados.add(escopo);
            }
        }
        return new ContaServico(nome, normalizados);
    }
}
