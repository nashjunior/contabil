# infra/tls — TLS em todas as interfaces expostas (F0)

Piso: **TLS 1.2 mínimo, TLS 1.3 preferencial**, apenas suítes AEAD. Nenhum tráfego
em claro. Autoridade: [ADR-0020](../../docs/arquitetura-tecnica/adr/0020-f0-tls-backup-imutavel-restauracao.md).
Certificados e chaves privadas **nunca** são versionados — vêm do cofre/ACME.

## Matriz de interfaces

| Interface | Config exemplo | Enforce |
| --- | --- | --- |
| Cliente → sistema (HTTP público) | `nginx-razao.conf.exemplo` | terminação TLS na borda, HSTS, 80→443 |
| App (Spring) — TLS nativo / gestão | `application-tls.yml.exemplo` | `server.ssl`, actuator em porta TLS separada |
| App → PostgreSQL (JDBC) | `application-tls.yml.exemplo` (nota) | `sslmode=verify-full` + `sslrootcert` na `DB_URL` |
| PostgreSQL (servidor) | `postgresql-tls.conf.exemplo` | `ssl=on`, cert de servidor, cifras fortes |
| App → publicação (Transparência/PNCP/SICONFI) | — | HTTPS obrigatório no cliente HTTP; sem downgrade |

## App → PostgreSQL: TLS pela `DB_URL`

O datasource já lê `DB_URL` (env). Habilite TLS no **próprio JDBC URL**, sem tocar
o `application.yml` base:

```
DB_URL=jdbc:postgresql://db.interno:5432/razao?sslmode=verify-full&sslrootcert=/etc/razao/tls/ca.crt
```

`verify-full` valida a cadeia **e** o hostname — impede MITM na rede interna.

## Dois modos de terminação na borda

- **Proxy reverso** (`nginx-razao.conf.exemplo`): recomendado quando há vários
  serviços atrás da borda; o app pode escutar HTTP só em `127.0.0.1`/rede interna.
- **TLS nativo do Spring** (`application-tls.yml.exemplo`): quando o app é exposto
  direto, sem proxy. Ative o profile e injete keystore/senha pelo cofre.

Em ambos os modos o endpoint de **gestão (actuator)** vai para porta separada com
TLS e bind restrito à rede de operação — nunca exposto na borda pública.
