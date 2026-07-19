// razao-infra — adaptadores de persistência do razão.
// JdbcTemplate (não JPA): batch insert dos lançamentos e a função de
// numeração exigem controle explícito de SQL que o mapeamento de entidade
// atrapalharia; o modelo append-only também não precisa de gerenciamento de
// entidade/dirty-checking.
dependencies {
    api(project(":razao:razao-application"))
    implementation(project(":plataforma:plataforma-infra"))

    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
