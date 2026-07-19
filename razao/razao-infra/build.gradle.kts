// razao-infra — adaptadores de persistência do razão.
dependencies {
    api(project(":razao:razao-application"))
    implementation(project(":plataforma:plataforma-infra"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}
