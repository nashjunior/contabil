rootProject.name = "razao"

// -----------------------------------------------------------------------------
// Monólito modular (ADR-0002): base contábil única, contextos isolados por
// fronteiras domain / application / infra. As dependências entre módulos
// (declaradas nos build.gradle.kts de cada módulo) SÃO a fronteira arquitetural.
// -----------------------------------------------------------------------------

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
    }
}

// Composition root: única aplicação executável (Spring Boot).
include("bootstrap")

// Contexto: plataforma (shared kernel — dinheiro, tenancy/RLS, outbox, eventos).
include("plataforma:plataforma-domain")
include("plataforma:plataforma-application")
include("plataforma:plataforma-infra")

// Contexto: razão (livro razão — partidas dobradas, append-only, Σdébito = Σcrédito).
include("razao:razao-domain")
include("razao:razao-application")
include("razao:razao-infra")

// Contexto: execução (execução orçamentária/financeira).
include("execucao:execucao-domain")
include("execucao:execucao-application")
include("execucao:execucao-infra")

// Guardrails: testes de arquitetura (ArchUnit) que FALHAM o build (RAZ-2, arquitetura-tecnica §8).
include("guardrails:architecture-tests")
