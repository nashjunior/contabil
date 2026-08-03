// -----------------------------------------------------------------------------
// Guardrails de arquitetura (fitness functions / ArchUnit) — RAZ-2.
//
// Depende de TODOS os módulos de produção para o ArchUnit importar o bytecode
// REAL (as regras não passam por vacuidade). Ligado ao ciclo `check`:
// `./gradlew check` roda estes guardrails e FALHA o build em qualquer violação
// (arquitetura-tecnica §8). Toolchain, java-library e JUnit 5 vêm da convenção
// da raiz (subprojects{} em build.gradle.kts).
// -----------------------------------------------------------------------------

dependencies {
    testImplementation(project(":execucao:execucao-domain"))
    testImplementation(project(":execucao:execucao-application"))
    testImplementation(project(":execucao:execucao-infra"))
    testImplementation(project(":razao:razao-domain"))
    testImplementation(project(":razao:razao-application"))
    testImplementation(project(":razao:razao-infra"))
    testImplementation(project(":plataforma:plataforma-domain"))
    testImplementation(project(":plataforma:plataforma-application"))
    testImplementation(project(":plataforma:plataforma-infra"))
    testImplementation(project(":prestacao-contas:prestacao-contas-domain"))
    testImplementation(project(":prestacao-contas:prestacao-contas-application"))
    testImplementation(project(":prestacao-contas:prestacao-contas-infra"))

    testImplementation(libs.archunit.junit5)
}
