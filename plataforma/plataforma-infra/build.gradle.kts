// plataforma-infra — adaptadores transversais (persistência, tenancy/RLS, outbox).
// Única camada da plataforma autorizada a conhecer Spring/JPA.
dependencies {
    api(project(":plataforma:plataforma-application"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Incorporação PAdES do PKCS#7 no PDF (ServicoAssinaturaGovBrAvancada) — RAZ-34.
    implementation(libs.pdfbox)
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.retry)
    // Object store S3-compatível (ADR-0018, RAZ-32). BOM fixa as versões do SDK.
    implementation(platform(libs.awssdk.bom))
    implementation(libs.awssdk.s3)
    implementation(libs.awssdk.url.connection.client)
}
