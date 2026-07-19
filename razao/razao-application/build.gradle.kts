// razao-application — casos de uso do razão.
dependencies {
    api(project(":razao:razao-domain"))
    implementation(project(":plataforma:plataforma-application"))

    // Só a demarcação transacional declarativa (@Transactional) e o
    // estereótipo de bean (@Service) — nada de JPA/Hibernate aqui, isso é
    // exclusivo da infra (ArchUnit dominio_nao_depende_de_framework só
    // restringe o domain, mas mantemos a application enxuta mesmo assim).
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
}
