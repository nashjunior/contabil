import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

// -----------------------------------------------------------------------------
// Build de convenção da raiz. Aplica as regras comuns a TODO módulo:
//   - toolchain Java 21 (ADR-0012)
//   - BOM do Spring Boot (versões gerenciadas, fonte única)
//   - JUnit 5, encoding UTF-8, -parameters
// Módulos individuais declaram apenas suas dependências (= fronteiras, ADR-0002).
// -----------------------------------------------------------------------------

plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

val javaLanguageVersion = (property("javaLanguageVersion") as String).toInt()

allprojects {
    group = "br.contabil"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "io.spring.dependency-management")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(javaLanguageVersion))
        }
        withSourcesJar()
    }

    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom(SpringBootPlugin.BOM_COORDINATES)
        }
    }

    dependencies {
        add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // CofreSegredosEnvironmentPostProcessor agora é de fato descoberto pelo Spring
        // Boot (META-INF/spring.factories corrigido, RAZ-211 — antes nunca rodava,
        // então nenhum teste precisava disso). Assinatura/login gov.br real estão fora
        // do escopo de qualquer teste JVM hoje (nenhum configura client-id) — em
        // branco, o cofre pula essas duas refs em vez de exigir um segredo que não
        // existe no ambiente de teste. Testes que legitimamente exercitam a resolução
        // desses segredos continuam livres para sobrescrever via `environment(...)`.
        environment("GOVBR_ASSINATURA_OAUTH_CLIENT_SECRET_REF", "")
        environment("GOVBR_LOGIN_OAUTH_CLIENT_SECRET_REF", "")
        // Datasource/Flyway: os testes `@SpringBootTest` com Testcontainers sobrescrevem
        // `spring.datasource.password`/`spring.flyway.password` via `@DynamicPropertySource`
        // — mas isso roda DEPOIS do EnvironmentPostProcessor (fase de contexto, não de
        // ambiente), então o cofre sempre vê essas propriedades "vazias" no momento em que
        // decide se resolve a `-ref` e tentaria resolver de verdade sem estas variáveis.
        // O valor aqui nunca chega a ser usado numa conexão real — o `@DynamicPropertySource`
        // de cada teste sobrescreve por cima antes do `refresh()` do contexto.
        environment("SIAFIC_F0_DB_RUNTIME_PASSWORD", "nao-usado-dynamicpropertysource-sobrescreve")
        environment("SIAFIC_F0_DB_MIGRATION_PASSWORD", "nao-usado-dynamicpropertysource-sobrescreve")
    }
}
