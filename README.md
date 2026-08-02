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

Variáveis de ambiente de banco:

| Var | Uso |
|-----|-----|
| `DB_RUNTIME_URL` | JDBC da aplicação, com `sslmode=require`, `verify-ca` ou `verify-full` |
| `DB_RUNTIME_USER` | login membro de `app_role`, sem DDL |
| `DB_RUNTIME_PASSWORD` | segredo injetado pelo cofre/ambiente |
| `DB_MIGRATION_URL` | JDBC usado só pelo Flyway, com `sslmode=require`, `verify-ca` ou `verify-full` |
| `DB_MIGRATION_USER` | login dono do schema, usado só em migração/CI |
| `DB_MIGRATION_PASSWORD` | segredo injetado pelo cofre/ambiente |
| `IAM_ENABLED` | liga o adapter IAM gov.br/ICP-Brasil; default `false` mantém fail-closed |
| `GOVBR_IAM_AUDIENCE` | audience esperada no JWT gov.br |
| `GOVBR_IAM_PUBLIC_KEY_PEM` | chave pública PEM para validar JWT gov.br RS256 |
| `GOVBR_IAM_ISSUER` | issuer gov.br esperado; default staging |
| `SIAFIC_DEV_IDP_ENABLED` | liga o IdP de dev local (`POST /sessao/dev-idp/token`, RAZ-228/ADR-0052); default `false` — **nunca ligar em staging/produção** |
| `SIAFIC_DEV_IDP_PRIVATE_KEY_PEM` | chave PRIVADA de dev (PKCS8 PEM) para assinar os JWTs do IdP de dev — par da mesma `GOVBR_IAM_PUBLIC_KEY_PEM` acima; só existe neste processo, nunca no navegador |
| `SERVER_PORT` | porta HTTP; default `8080` |

O runtime e o Flyway devem usar usuários distintos. O runtime não deve ser dono
do schema e não deve ter DDL; a conta de migração não é usada pela aplicação em
execução. Não há default literal para senha.

IAM/RBAC fica desligado por padrão até o ente provisionar `siafic.iam.concessoes`
com CPF, ente e papéis. Com `IAM_ENABLED=true`, o sistema valida JWT gov.br
assinado (`RS256`), aceita certificado ICP-Brasil apenas quando o fingerprint
SHA-256 foi provisionado e rejeita combinações de papéis conflitantes
(`LANCADOR` + `AUTORIZADOR`, `AUTORIZADOR` + `PAGADOR`, `ADMIN_ACESSO` +
operação financeira).

**IdP de dev local** (`POST /sessao/dev-idp/token`, RAZ-228/ADR-0052 item 1):
substitui, para o formulário "Modo desenvolvimento" do SPA, a string opaca que o
backend real rejeitava por um JWT RS256 de verdade — reaproveita a mesma
mecânica já provada pela suíte e2e (`frontend/e2e/support/jwt.ts`), agora
alcançável por HTTP. **Nunca ativo por acidente**: exige as DUAS flags ao mesmo
tempo, `SIAFIC_DEV_IDP_ENABLED=true` **e** `IAM_ENABLED=true` com o par de
chaves de dev completo (`GOVBR_IAM_ISSUER`/`GOVBR_IAM_AUDIENCE`/
`GOVBR_IAM_PUBLIC_KEY_PEM` — mesmas env vars do e2e — mais
`SIAFIC_DEV_IDP_PRIVATE_KEY_PEM`, a metade privada do mesmo par). Sem as duas
flags, a rota nem existe (`404`, fail-closed por ausência, não por checagem em
runtime); com as duas ligadas mas o par de chaves incompleto ou desparelhado
com a chave pública configurada, o processo recusa subir. Em staging/produção,
onde `GOVBR_IAM_*` aponta para a chave pública real do gov.br, uma ativação
acidental da flag falharia de qualquer forma ao validar o par de chaves — a
flag reduz ainda mais o raio do acidente. Nunca ligar em staging/produção.

Postgres local rápido:

```bash
export LOCAL_DB_MIGRATION_PASSWORD='<defina-no-seu-cofre-local>'
docker run --rm -p 5432:5432 \
  -e POSTGRES_DB=razao -e POSTGRES_USER=razao_migration -e POSTGRES_PASSWORD="$LOCAL_DB_MIGRATION_PASSWORD" \
  postgres:16
```

Exemplo local mínimo após criar o usuário de runtime e habilitar TLS no
PostgreSQL local:

```bash
export DB_RUNTIME_URL='jdbc:postgresql://localhost:5432/razao?sslmode=require'
export DB_RUNTIME_USER='razao_app'
export DB_RUNTIME_PASSWORD='<segredo-runtime-do-cofre-local>'
export DB_MIGRATION_URL='jdbc:postgresql://localhost:5432/razao?sslmode=require'
export DB_MIGRATION_USER='razao_migration'
export DB_MIGRATION_PASSWORD="$LOCAL_DB_MIGRATION_PASSWORD"
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
