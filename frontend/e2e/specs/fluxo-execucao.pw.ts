/**
 * RAZ-211 — fluxo empenho→liquidação→pagamento→aprovação→consulta contra o
 * backend real (ver `global-setup.ts`), autenticado com JWTs RS256 reais
 * verificados por `VerificadorJwtGovBr` + RBAC real de `IamProperties` (não um
 * duplo de teste que sempre autoriza, como os testes JVM existentes usam).
 *
 * Empenho e a consulta final rodam pelo DOM (navegador real); liquidação/
 * aprovação/pagamento vão por HTTP real direto (`support/apiExecucao.ts`) porque
 * essas 3 etapas ainda não têm tela própria (gap documentado em
 * `frontend/README.md` "Gaps sinalizados").
 *
 * Esta suíte foi a primeira prova fim-a-fim com RBAC real neste repo e descobriu
 * o gap RAZ-222 (nenhum papel concedia `CRIAR` em `execucao:liquidacao`, e o gate
 * de aprovação exigia `PAGADOR` — que a Regra 9/ADR-0023 reservam para "quem
 * paga", não "quem autoriza"). RAZ-222 corrigiu a matriz em `IamProperties.Papel`
 * (LANCADOR ganha `CRIAR` em `execucao:liquidacao`; AUTORIZADOR ganha `APROVAR`
 * em `execucao:pagamento`, alinhado à tabela §2 de
 * `fluxo-execucao-operador-contrato-api.md`) — o segundo teste abaixo exercita o
 * fluxo completo com o lançador e o autorizador como atores distintos.
 */
import { test, expect } from '@playwright/test';
import { carregarRuntime } from '../support/runtime';
import {
  buscarEmpenhoPorHistorico,
  buscarLiquidacaoPorHistorico,
  buscarPagamentoPorHistorico,
  liquidar,
  aprovar,
  pagar,
} from '../support/apiExecucao';

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

  test('liquidação → aprovação → pagamento → consulta via API real, com RBAC real (RAZ-222)', async ({ request }) => {
    const lancador = runtime.usuarios.enteALancador;
    const autorizador = runtime.usuarios.enteAAutorizador;
    const historicoEmpenho = `Empenho E2E RAZ-211 (p/ liquidação) ${Date.now()}`;
    // Cria o empenho por HTTP direto aqui (não é o foco deste teste) só para ter um
    // empenhoId real ao qual liquidar.
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

    // LANCADOR cria a liquidação (RAZ-222 Gap 1: agora concedido em IamProperties.Papel).
    const historicoLiquidacao = `Liquidação E2E RAZ-211 ${Date.now()}`;
    const liquidacao = await liquidar(request, runtime.backendBaseUrl, lancador, {
      empenhoId,
      valor: '4200.30',
      historico: historicoLiquidacao,
    });
    expect(liquidacao.status).toBe('pendente');

    // AUTORIZADOR aprova — ator distinto do lançador (Regra 9/ADR-0023; RAZ-222 Gap 2:
    // o gate exigia PAGADOR, que conflita com AUTORIZADOR e nunca podia aprovar de fato).
    const aprovacao = await aprovar(request, runtime.backendBaseUrl, autorizador, liquidacao.id);
    expect(aprovacao.status).toBe(200);
    expect(aprovacao.corpo.status).toBe('aprovada');

    // PAGADOR efetiva a baixa — `lancador` também carrega o papel PAGADOR na fixture
    // (`support/fixtures.ts`), ator distinto de quem aprovou.
    const historicoPagamento = `Pagamento E2E RAZ-211 ${Date.now()}`;
    const pagamento = await pagar(request, runtime.backendBaseUrl, lancador, {
      liquidacaoId: liquidacao.id,
      valor: '4200.30',
      historico: historicoPagamento,
      idempotencyKey: crypto.randomUUID(),
    });
    expect(pagamento.valor).toBe('4200.30');

    // Consulta: liquidação e pagamento lidos de volta do backend real refletem o
    // estado final do fluxo (aprovada + baixa efetivada).
    const liquidacaoConsultada = await buscarLiquidacaoPorHistorico(
      request,
      runtime.backendBaseUrl,
      lancador,
      historicoLiquidacao,
    );
    expect(liquidacaoConsultada.statusAprovacao).toBe('aprovada');

    const pagamentoConsultado = await buscarPagamentoPorHistorico(
      request,
      runtime.backendBaseUrl,
      lancador,
      historicoPagamento,
    );
    expect(pagamentoConsultado.liquidacaoId).toBe(liquidacao.id);
    expect(pagamentoConsultado.valor).toBe('4200.30');
  });
});
