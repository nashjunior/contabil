/**
 * Sobe o par backend real + Postgres real (Testcontainers, RAZ-211) UMA VEZ para
 * toda a suíte E2E, autentica com JWTs RS256 assinados localmente (mesmo contrato
 * de `VerificadorJwtGovBr` — ver `support/jwt.ts`) e escreve `.runtime.json` para
 * os specs lerem. O smoke test contra o gov.br staging real é um gap OPERACIONAL
 * diferente e deliberadamente fora deste escopo (ver
 * docs/operacao/RAZ-126-runbook-oidc-login-govbr.md).
 *
 * Não sobe o frontend aqui — cada worker do Playwright já aponta o navegador para
 * a `baseURL` do `webServer` do `playwright.config.ts` (que o próprio Playwright
 * gerencia), este setup só precisa saber a porta do BACKEND para repassar ao
 * `vite dev` via env `VITE_API_BASE_URL`.
 */
import { spawn, type ChildProcessWithoutNullStreams } from 'node:child_process';
import { mkdirSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PostgreSqlContainer, type StartedPostgreSqlContainer } from '@testcontainers/postgresql';
import { AssinadorJwtE2e, AUDIENCE, ISSUER } from './support/jwt';
import { USUARIOS } from './support/fixtures';
import { APP_LOGIN_PASSWORD, concederPapelDeRuntime, criarPapelDeRuntime, semearDadosBase } from './support/db';
import { aguardarHttp200 } from './support/wait';
import { BACKEND_PORT } from './support/ports';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..', '..');

function resolverBootJar(): string {
  const libsDir = join(REPO_ROOT, 'bootstrap', 'build', 'libs');
  const candidato = readdirSync(libsDir).find((nome) => nome.endsWith('.jar') && !nome.endsWith('-plain.jar'));
  if (!candidato) {
    throw new Error(
      `Nenhum bootJar em ${libsDir} — rode "./gradlew :bootstrap:bootJar" na raiz do repo antes de "npm run test:e2e".`,
    );
  }
  return join(libsDir, candidato);
}

function logarProcesso(nome: string, proc: ChildProcessWithoutNullStreams): void {
  proc.stdout.on('data', (chunk) => process.stdout.write(`[${nome}] ${chunk}`));
  proc.stderr.on('data', (chunk) => process.stderr.write(`[${nome}] ${chunk}`));
}

export default async function globalSetup(): Promise<() => Promise<void>> {
  const jarPath = resolverBootJar();

  const postgres: StartedPostgreSqlContainer = await new PostgreSqlContainer('postgres:16')
    .withDatabase('siafic_e2e')
    .withUsername('postgres')
    .withPassword('postgres_e2e_pw')
    .start();

  await criarPapelDeRuntime(postgres);

  const assinador = new AssinadorJwtE2e();
  const jdbcUrl = `jdbc:postgresql://${postgres.getHost()}:${postgres.getPort()}/${postgres.getDatabase()}`;

  const backendEnv: NodeJS.ProcessEnv = {
    ...process.env,
    SERVER_PORT: String(BACKEND_PORT),
    DB_RUNTIME_URL: jdbcUrl,
    DB_RUNTIME_USER: 'app_login',
    DB_MIGRATION_URL: jdbcUrl,
    DB_MIGRATION_USER: postgres.getUsername(),
    // cofre F0 (`CofreSegredosVariaveisAmbiente`): cofre://siafic/f0/db/{runtime,migration}/password
    // vira SIAFIC_F0_DB_{RUNTIME,MIGRATION}_PASSWORD (maiúsculo, "/" -> "_").
    SIAFIC_F0_DB_RUNTIME_PASSWORD: APP_LOGIN_PASSWORD,
    SIAFIC_F0_DB_MIGRATION_PASSWORD: postgres.getPassword(),
    DB_REQUIRE_SSL: 'false',
    IAM_ENABLED: 'true',
    GOVBR_IAM_ISSUER: ISSUER,
    GOVBR_IAM_AUDIENCE: AUDIENCE,
    GOVBR_IAM_PUBLIC_KEY_PEM: assinador.publicKeyPem,
    CORS_ALLOWED_ORIGINS: 'http://localhost:4173',
    OUTBOX_WORKER_ENABLED: 'false',
    // Fora de escopo do RAZ-211 (login/assinatura gov.br real, ver comentário no topo
    // deste arquivo) — em branco, o cofre pula essas duas refs em vez de exigir um
    // segredo que este harness nunca usa (CofreSegredosEnvironmentPostProcessor).
    GOVBR_ASSINATURA_OAUTH_CLIENT_SECRET_REF: '',
    GOVBR_LOGIN_OAUTH_CLIENT_SECRET_REF: '',
  };

  const concessaoArgs = Object.values(USUARIOS).flatMap((usuario, indice) => [
    `--siafic.iam.concessoes[${indice}].cpf=${usuario.cpf}`,
    `--siafic.iam.concessoes[${indice}].enteId=${usuario.enteId}`,
    ...usuario.papeis.map((papel, papelIndice) => `--siafic.iam.concessoes[${indice}].papeis[${papelIndice}]=${papel}`),
  ]);

  const backend = spawn('java', ['-jar', jarPath, ...concessaoArgs], {
    env: backendEnv,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  logarProcesso('backend', backend);
  backend.on('exit', (code) => {
    if (code !== null && code !== 0) {
      // eslint-disable-next-line no-console
      console.error(`[backend] saiu com código ${code} antes do teardown — ver logs acima.`);
    }
  });

  try {
    await aguardarHttp200(`http://localhost:${BACKEND_PORT}/actuator/health`, 90_000);
  } catch (erro) {
    backend.kill('SIGTERM');
    await postgres.stop();
    throw erro;
  }

  await concederPapelDeRuntime(postgres);
  const dotacaoIdPorEnte = await semearDadosBase(postgres);

  const runtime = {
    backendBaseUrl: `http://localhost:${BACKEND_PORT}`,
    dotacaoIdPorEnte,
    usuarios: Object.fromEntries(
      Object.entries(USUARIOS).map(([chave, usuario]) => [
        chave,
        { ...usuario, jwt: assinador.assinar(usuario) },
      ]),
    ),
  };
  const runtimeDir = join(__dirname);
  mkdirSync(runtimeDir, { recursive: true });
  writeFileSync(join(runtimeDir, '.runtime.json'), JSON.stringify(runtime, null, 2));

  return async function globalTeardown(): Promise<void> {
    backend.kill('SIGTERM');
    await new Promise((resolve) => backend.once('exit', resolve));
    await postgres.stop();
  };
}
