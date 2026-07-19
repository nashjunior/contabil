// plataforma-infra — adaptadores transversais (persistência, tenancy/RLS, outbox).
// Única camada da plataforma autorizada a conhecer Spring/JPA.
dependencies {
    api(project(":plataforma:plataforma-application"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Incorporação PAdES do PKCS#7 no PDF (ServicoAssinaturaGovBrAvancada) — RAZ-34.
    implementation(libs.pdfbox)
}
