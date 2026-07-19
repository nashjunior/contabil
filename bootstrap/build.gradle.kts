// bootstrap — composition root: única aplicação executável (Spring Boot).
// Conhece todos os *-infra e é dona da configuração e das migrações Flyway
// (base contábil única, ADR-0002). Nenhum módulo depende de bootstrap.
plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":plataforma:plataforma-infra"))
    implementation(project(":razao:razao-infra"))
    implementation(project(":execucao:execucao-infra"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
}
