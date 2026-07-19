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
    }
}
