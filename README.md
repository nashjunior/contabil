# Razão — sistema contábil público (SIAFIC)

Sistema contábil (padrão SIAFIC, Decreto 10.540/2020) construído como
**monólito modular** na JVM/Spring, com base contábil única. Fundação criada em
RAZ-1.

## Requisitos

- **JDK 21** (a toolchain do Gradle baixa/valida a versão correta).
- **PostgreSQL** para rodar a aplicação (o build não precisa de banco).

## Build

```bash
./gradlew build          # compila todos os módulos + testes + bootJar
./gradlew :bootstrap:bootRun   # sobe a aplicação (requer Postgres — ver abaixo)
```

Variáveis de ambiente (defaults de dev local entre parênteses):

| Var | Default |
|-----|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/razao` |
| `DB_USER` | `razao` |
| `DB_PASSWORD` | `razao` |
| `SERVER_PORT` | `8080` |

Postgres local rápido:

```bash
docker run --rm -p 5432:5432 \
  -e POSTGRES_DB=razao -e POSTGRES_USER=razao -e POSTGRES_PASSWORD=razao \
  postgres:16
```

## Estrutura

Ver [`AGENTS.md`](AGENTS.md) (convenções e invariantes) e
[`docs/arquitetura-tecnica/`](docs/arquitetura-tecnica/) (ADRs — autoridade das
decisões). Resumo dos contextos × camadas:

```
bootstrap/    composition root (Spring Boot, config, migrações Flyway)
plataforma/   shared kernel + transversais (domain/application/infra)
razao/        livro razão — partidas dobradas, append-only (domain/application/infra)
execucao/     execução orçamentária/financeira (domain/application/infra)
```

## Decisões de arquitetura

- [ADR-0002 — Monólito modular](docs/arquitetura-tecnica/adr/ADR-0002-monolito-modular.md)
- [ADR-0012 — Plataforma JVM (Java 21 + Spring Boot + Gradle + Flyway)](docs/arquitetura-tecnica/adr/ADR-0012-plataforma-jvm-spring.md)
