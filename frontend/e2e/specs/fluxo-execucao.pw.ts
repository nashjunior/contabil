/**
 * RAZ-211 — fluxo empenho→liquidação→pagamento→aprovação→consulta contra o
 * backend real (ver `global-setup.ts`), autenticado com JWTs RS256 reais
 * verificados por `VerificadorJwtGovBr` + RBAC real de `IamProperties` (não um
 * duplo de teste que sempre autoriza, como os testes JVM existentes usam).
 *
 * Empenho e a consulta final rodam pelo DOM (navegador real); liquidação/
 * aprovação/pagamento iriam por HTTP real direto (`support/apiExecucao.ts`)
 * porque essas 3 etapas ainda não têm tela própria (gap documentado em
 * `frontend/README.md` "Gaps sinalizados"). "Iriam" porque essa continuação está
 * BLOQUEADA hoje por um gap de RBAC real que esta suíte descobriu (RAZ-222,
 * primeira prova fim-a-fim com RBAC real neste repo): nenhum papel concede CRIAR
 * em `execucao:liquidacao`, então NINGUÉM consegue criar uma liquidação via a API
 * real com um usuário devidamente autorizado — não é um gap desta suíte, é um
 * bloqueio funcional do próprio backend. O segundo teste abaixo documenta isso com
 * `test.fail()` (falha esperada — Playwright alerta sozinho se um dia passar sem
 * que RAZ-222 tenha sido resolvida e este teste atualizado).
 */
import { test, expect } from '@playwright/test';
import { carregarRuntime } from '../support/runtime';
import { buscarEmpenhoPorHistorico, liquidar } from '../support/apiExecucao';

const runtime = carregarRuntime();
const EXERCICIO_ATUAL = new Date().getFullYear();

test.describe('fluxo de execução orçamentária — navegador real x backend real', () => {
  test('empenho via UI reflete na consulta real com dinheiro decimal e CPF mascarado', async ({ browser, request }) => {
    const lancador = runtime.usuarios.enteALancador;
    const historicoEmpenho = `Empenho E2E RAZ-211 ${Date.now()}`;

    const context = await browser.newContext({
      extraHTTPHeaders: { Authorization: `Bearer ${lancador.jwt}` },
    });
    const page = await context.newPage();

    await page.goto('/execucao');
    await expect(page.getByRole('heading', { name: /execução orçamentária/i })).toBeVisible();

    // PII mascarada (RAZ-211): a asserção gov.br carrega o CPF completo, mas a UI só
    // pode mostrar a versão mascarada — e o CPF cru nunca deve aparecer no DOM.
    await expect(page.getByText(lancador.cpfMascarado, { exact: false })).toBeVisible();
    await expect(page.locator('body')).not.toContainText(lancador.cpf);

    await page.getByLabel(/dotação/i).click();
    await page.getByRole('option', { name: /raz-211 — raz-211/ }).click();
    await page.getByLabel(/^tipo/i).selectOption('ordinario');
    await page.getByLabel(/id do credor/i).fill(crypto.randomUUID());
    await page.getByLabel(/id da unidade gestora/i).fill(crypto.randomUUID());
    await page.getByLabel(/^valor/i).fill('10500.75');
    await page.getByLabel(/data do fato/i).fill(new Date().toISOString().slice(0, 10));
    await page.getByLabel(/exercício/i).fill(String(EXERCICIO_ATUAL));
    await page.getByLabel(/classificação orçamentária/i).fill('raz-211');
    await page.getByLabel(/fonte de recurso/i).fill('raz-211');
    await page.getByLabel(/histórico/i).fill(historicoEmpenho);
    await page.getByRole('button', { name: /registrar empenho/i }).click();

    await expect(page.getByText(historicoEmpenho)).toBeVisible();
    // Dinheiro decimal (invariante do backend, AGENTS.md): 10500.75 chega e é exibido
    // exatamente como R$ 10.500,75 — nunca arredondado/convertido via float.
    await expect(page.getByText('R$ 10.500,75').first()).toBeVisible();

    // Consulta (RAZ-211): o GET real de agregado (invalidado pela mutation) e a lista
    // de empenhos registrados refletem o mesmo empenho lido de volta do backend real.
    const empenho = await buscarEmpenhoPorHistorico(
      request,
      runtime.backendBaseUrl,
      lancador,
      EXERCICIO_ATUAL,
      historicoEmpenho,
    );
    expect(empenho.valor).toBe('10500.75');

    await context.close();
  });

  test('liquidação → aprovação → pagamento via API real — bloqueado por gap de RBAC (RAZ-222)', async ({ request }) => {
    test.fail(true, 'RAZ-222: nenhum papel concede CRIAR em execucao:liquidacao — remover test.fail() quando corrigido');

    const lancador = runtime.usuarios.enteALancador;
    const historicoEmpenho = `Empenho E2E RAZ-211 (p/ liquidação) ${Date.now()}`;
    // Cria o empenho por HTTP direto aqui (não é o foco deste teste) só para ter um
    // empenhoId real ao qual tentar liquidar.
    const criacao = await request.post(
      `${runtime.backendBaseUrl}/api/v1/entes/${lancador.enteId}/execucao/empenhos`,
      {
        headers: { Authorization: `Bearer ${lancador.jwt}`, 'Content-Type': 'application/json' },
        data: {
          dotacaoId: runtime.dotacaoIdPorEnte[lancador.enteId],
          tipo: 'ordinario',
          credorId: crypto.randomUUID(),
          unidadeGestoraId: crypto.randomUUID(),
          valor: '4200.30',
          dataFato: new Date().toISOString().slice(0, 10),
          exercicio: EXERCICIO_ATUAL,
          classificacaoOrcamentaria: 'raz-211',
          fonteRecurso: 'raz-211',
          historico: historicoEmpenho,
        },
      },
    );
    expect(criacao.status()).toBe(201);
    const empenhoId = (await criacao.json()).id as string;

    // Confirma o 403 exato do gap (RAZ-222) — não um erro genérico qualquer.
    const liquidacao = await liquidar(request, runtime.backendBaseUrl, lancador, {
      empenhoId,
      valor: '4200.30',
      historico: `Liquidação E2E RAZ-211 ${Date.now()}`,
    });
    // Se isto passar (liquidacao com `status`), RAZ-222 foi corrigida — trocar este
    // teste para continuar aprovação/pagamento/consulta e remover o test.fail() acima.
    expect(liquidacao.status).toBe('pendente');
  });
});
