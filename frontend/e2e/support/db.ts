import { randomUUID } from 'node:crypto';
import type { StartedPostgreSqlContainer } from '@testcontainers/postgresql';
import { CONTAS_PCASP, ENTE_A, ENTE_B } from './fixtures';

export const APP_LOGIN_PASSWORD = 'app_login_e2e_pw';

/** SQL para provisionar `app_login`: precisa existir ANTES do primeiro boot do
 * backend (o pool Hikari de `spring.datasource.*` conecta como `app_login` — se o
 * papel não existir, o boot inteiro falha), mas só ganha `app_role` (criado pela
 * migração Flyway) depois que o backend já rodou pelo menos uma vez. Mesmo padrão
 * de `ExecucaoEscritaHttpIntegrationTest.migraESemeiaBase()`, só que fora da JVM. */
async function psql(container: StartedPostgreSqlContainer, sql: string): Promise<void> {
  const resultado = await container.exec(['psql', '-v', 'ON_ERROR_STOP=1', '-U', container.getUsername(), '-d', container.getDatabase(), '-c', sql]);
  if (resultado.exitCode !== 0) {
    throw new Error(`psql falhou (exit ${resultado.exitCode}): ${sql}\n${resultado.output}`);
  }
}

export async function criarPapelDeRuntime(container: StartedPostgreSqlContainer): Promise<void> {
  await psql(container, `CREATE ROLE app_login LOGIN PASSWORD '${APP_LOGIN_PASSWORD}'`);
}

export async function concederPapelDeRuntime(container: StartedPostgreSqlContainer): Promise<void> {
  await psql(container, 'GRANT app_role TO app_login');
}

/** Semeia cadastros-base (ente, período contábil aberto no mês corrente, contas
 * PCASP, uma dotação) para os dois entes de teste — nenhum desses tem endpoint de
 * cadastro no backend ainda (mesmo gap documentado em `ExecucaoEscritaHttpIntegrationTest`),
 * então a semeadura é SQL direto, exatamente como o teste JVM de referência faz. */
export async function semearDadosBase(container: StartedPostgreSqlContainer): Promise<Record<string, string>> {
  const agora = new Date();
  const exercicio = agora.getFullYear();
  const mes = agora.getMonth() + 1;
  const dotacaoIdPorEnte: Record<string, string> = {};

  for (const [enteId, nome, cnpj] of [
    [ENTE_A, 'Prefeitura Municipal de Exemplo (E2E RAZ-211)', '11111111000191'],
    [ENTE_B, 'Secretaria Estadual de Exemplo (E2E RAZ-211)', '22222222000172'],
  ] as const) {
    await psql(
      container,
      `insert into ente (id, cnpj, nome, esfera) values ('${enteId}', '${cnpj}', '${nome}', 'municipio')`,
    );
    await psql(
      container,
      `insert into periodo_contabil (ente_id, exercicio, mes, status) values ('${enteId}', ${exercicio}, ${mes}, 'aberto')`,
    );
    for (const [codigo, descricao, natureza] of CONTAS_PCASP) {
      await psql(
        container,
        `insert into conta_pcasp (ente_id, codigo, descricao, natureza_informacao, natureza_saldo) ` +
          `values ('${enteId}', '${codigo}', '${descricao}', '${natureza}', 'D')`,
      );
    }
    const unidadeGestoraId = randomUUID();
    const resultado = await container.exec([
      'psql',
      '-v',
      'ON_ERROR_STOP=1',
      '-U',
      container.getUsername(),
      '-d',
      container.getDatabase(),
      '-t',
      '-A',
      '-c',
      `insert into dotacao (ente_id, exercicio, classificacao_orcamentaria, fonte_recurso, unidade_gestora_id, valor_autorizado) ` +
        `values ('${enteId}', ${exercicio}, 'raz-211', 'raz-211', '${unidadeGestoraId}', 500000.00) returning id`,
    ]);
    if (resultado.exitCode !== 0) {
      throw new Error(`semeadura de dotacao falhou para ${enteId}: ${resultado.output}`);
    }
    dotacaoIdPorEnte[enteId] = resultado.stdout.trim();
  }

  return dotacaoIdPorEnte;
}
