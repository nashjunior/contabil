package br.contabil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root do sistema contábil (Razão / SIAFIC).
 *
 * <p>É a única aplicação executável do monólito modular (ADR-0002). O component
 * scan a partir de {@code br.contabil} cobre todos os contextos
 * (plataforma, razao, execucao). A base contábil é única; as migrações Flyway
 * vivem neste módulo (ADR-0012).
 */
@SpringBootApplication(scanBasePackages = "br.contabil")
public class RazaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RazaoApplication.class, args);
    }
}
