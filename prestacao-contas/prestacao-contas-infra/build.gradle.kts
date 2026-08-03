// prestacao-contas-infra — adapters de serialização/empacotamento/publicação.
dependencies {
    api(project(":prestacao-contas:prestacao-contas-application"))
    implementation(project(":plataforma:plataforma-infra"))

    implementation("org.springframework.boot:spring-boot-starter")
}
