// bootstrap — composition root: única aplicação executável (Spring Boot).
// Conhece todos os *-infra e é dona da configuração e das migrações Flyway
// (base contábil única, ADR-0002). Nenhum módulo depende de bootstrap.
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    alias(libs.plugins.spring.boot)
}

// O BOM do Spring Boot 3.3 gerencia testcontainers em 1.19.8, cujo docker-java
// não negocia a API do Docker atual (exige >= 1.40) — importa o BOM próprio do
// testcontainers para fixar 1.20.x em toda a árvore, inclusive transitivos.
extensions.configure<DependencyManagementExtension> {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${libs.versions.testcontainers.get()}")
    }
}

dependencies {
    implementation(project(":plataforma:plataforma-infra"))
    implementation(project(":razao:razao-infra"))
    implementation(project(":execucao:execucao-infra"))
    implementation(project(":prestacao-contas:prestacao-contas-infra"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // AOP (aspectjweaver): transação declarativa nos use cases sem anotar a application.
    implementation("org.springframework.boot:spring-boot-starter-aop")

    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.retry)
    implementation(libs.logstash.logback.encoder)
    runtimeOnly(libs.postgresql)

    // Guardrail de CI contra bypass de RLS via FK (RAZ-13) — roda a migration
    // num Postgres real; pulado automaticamente se Docker não estiver disponível.
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}
