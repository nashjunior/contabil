// execucao-infra — adaptadores de persistência da execução.
dependencies {
    api(project(":execucao:execucao-application"))
    implementation(project(":plataforma:plataforma-infra"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation(libs.pdfbox)
    // PostgresLiquidacaoRepository serializa documentos_suporte (jsonb) — RAZ-105.
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
