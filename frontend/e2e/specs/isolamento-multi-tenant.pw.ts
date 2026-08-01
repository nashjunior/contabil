/**
 * RAZ-211 — isolamento multi-ente pelo navegador real: um empenho lançado com a
 * sessão do ente A nunca pode aparecer para uma sessão do ente B, mesmo os dois
 * apontando para o MESMO backend/Postgres. Complementa (não substitui) os testes
 * de RLS no banco (`VazamentoCrossTenantRlsTest`, `RazaoMultiTenantE2EIntegrationTest`)
 * — aqui a prova é fim-a-fim, através da borda HTTP + DOM que o usuário real usa.
 */
import { test, expect } from '@playwright/test';
import { carregarRuntime } from '../support/runtime';

const runtime = carregarRuntime();
const EXERCICIO_ATUAL = new Date().getFullYear();
const HISTORICO_ENTE_A = `Empenho isolamento ente A RAZ-211 ${Date.now()}`;

test.describe('isolamento multi-tenant — navegador real', () => {
  test('empenho do ente A nunca aparece na sessão do ente B', async ({ browser }) => {
    const usuarioA = runtime.usuarios.enteALancador;
    const usuarioB = runtime.usuarios.enteBLancador;

    const contextoA = await browser.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${usuarioA.jwt}` } });
    const paginaA = await contextoA.newPage();
    await paginaA.goto('/execucao');
    await expect(paginaA.getByRole('heading', { name: /execução orçamentária/i })).toBeVisible();

    await paginaA.getByLabel(/dotação/i).click();
    await paginaA.getByRole('option', { name: /raz-211 — raz-211/ }).click();
    await paginaA.getByLabel(/^tipo/i).selectOption('ordinario');
    await paginaA.getByLabel(/id do credor/i).fill(crypto.randomUUID());
    await paginaA.getByLabel(/id da unidade gestora/i).fill(crypto.randomUUID());
    await paginaA.getByLabel(/^valor/i).fill('777.00');
    await paginaA.getByLabel(/data do fato/i).fill(new Date().toISOString().slice(0, 10));
    await paginaA.getByLabel(/exercício/i).fill(String(EXERCICIO_ATUAL));
    await paginaA.getByLabel(/classificação orçamentária/i).fill('raz-211');
    await paginaA.getByLabel(/fonte de recurso/i).fill('raz-211');
    await paginaA.getByLabel(/histórico/i).fill(HISTORICO_ENTE_A);
    await paginaA.getByRole('button', { name: /registrar empenho/i }).click();
    await expect(paginaA.getByText(HISTORICO_ENTE_A)).toBeVisible();
    await contextoA.close();

    const contextoB = await browser.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${usuarioB.jwt}` } });
    const paginaB = await contextoB.newPage();
    await paginaB.goto('/execucao');
    await expect(paginaB.getByRole('heading', { name: /execução orçamentária/i })).toBeVisible();

    // Sessão do ente B hidratada (RAZ-203/205): confirma que estamos autenticados como
    // o CPF/ente B, não reaproveitando por engano o contexto do ente A.
    await expect(paginaB.getByText(usuarioB.cpfMascarado, { exact: false })).toBeVisible();

    await expect(paginaB.getByText(HISTORICO_ENTE_A)).toHaveCount(0);

    await contextoB.close();
  });
});
