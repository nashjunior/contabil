// razao-application — casos de uso do razão.
dependencies {
    api(project(":razao:razao-domain"))
    implementation(project(":plataforma:plataforma-application"))
    // Sem dependência de framework: os use cases são POJO. O wiring (@Bean) e a
    // transação (advisor na borda) vivem na infra/composition root — ADR-0002.
}
