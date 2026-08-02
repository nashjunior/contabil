/**
 * RAZ-211 — fluxo empenho→liquidação→pagamento→aprovação→consulta contra o
 * backend real (ver `global-setup.ts`), autenticado com JWTs RS256 reais
 * verificados por `VerificadorJwtGovBr` + RBAC real de `IamProperties` (não um
 * duplo de teste que sempre autoriza, como os testes JVM existentes usam).
 *
 * Esta suíte foi a primeira prova fim-a-fim com RBAC real neste repo e descobriu
 * o gap RAZ-222 (nenhum papel concedia `CRIAR` em `execucao:liquidacao`, e o gate
 * de aprovação exigia `PAGADOR` — que a Regra 9/ADR-0023 reservam para "quem
 * paga", não "quem autoriza"). RAZ-222 corrigiu a matriz em `IamProperties.Papel`
 * (LANCADOR ganha `CRIAR` em `execucao:liquidacao`; AUTORIZADOR ganha `APROVAR`
 * em `execucao:pagamento`, alinhado à tabela §2 de
 * `fluxo-execucao-operador-contrato-api.md`).
 *
 * Três testes, profundidade crescente:
 * 1. Empenho via DOM real → consulta reflete dinheiro decimal + CPF mascarado.
 * 2. Liquidação→aprovação→pagamento via HTTP direto (`support/apiExecucao.ts`) —
 *    rápido e estável, foco nas regras de negócio/RBAC (o que este teste prova
 *    não depende de nenhuma tela específica renderizar certo).
 * 3. O MESMO fluxo do (2), mas 100% pelas telas reais (RAZ-229/RAZ-230:
 *    `LiquidacaoPage`/`PagamentoPage`/`AprovacaoFilaPage`+`GateAprovacaoModal`,
 *    que não existiam quando esta suíte foi criada — gap fechado). Prova que as
 *    telas novas realmente se integram ao backend real (formulário RHF+zod,
 *    combos síncronos/assíncronos, modal do gate 4-eyes), algo que os testes de
 *    componente dessas telas (MSW) não alcançam.
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

  test('o mesmo fluxo 100% pelas telas reais: LiquidacaoPage → AprovacaoFilaPage/GateAprovacaoModal → PagamentoPage', async ({
    browser,
  }) => {
    const lancador = runtime.usuarios.enteALancador;
    const autorizador = runtime.usuarios.enteAAutorizador;
    // Ator distinto do lançador: `ConsultarFilaAprovacao` exclui o autor da própria
    // consulta incondicionalmente (Regra 9), inclusive do filtro `aprovada` que
    // alimenta o `LiquidacaoAprovadaPicker` — o lançador nunca veria a própria
    // liquidação nesse combo, mesmo carregando PAGADOR (ver `support/fixtures.ts`).
    const pagador = runtime.usuarios.enteAPagador;
    const hoje = new Date().toISOString().slice(0, 10);
    const valor = '6543.21';
    const valorFormatado = 'R$ 6.543,21';

    // --- Empenho (lançador) ---
    const ctxLancador = await browser.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${lancador.jwt}` } });
    const pageLancador = await ctxLancador.newPage();
    const historicoEmpenho = `Empenho UI E2E RAZ-211 ${Date.now()}`;

    await pageLancador.goto('/execucao');
    await pageLancador.getByLabel(/dotação/i).click();
    await pageLancador.getByRole('option', { name: /raz-211 — raz-211/ }).click();
    await pageLancador.getByLabel(/^tipo/i).selectOption('ordinario');
    await pageLancador.getByLabel(/id do credor/i).fill(crypto.randomUUID());
    await pageLancador.getByLabel(/id da unidade gestora/i).fill(crypto.randomUUID());
    await pageLancador.getByLabel(/^valor/i).fill(valor);
    await pageLancador.getByLabel(/data do fato/i).fill(hoje);
    await pageLancador.getByLabel(/exercício/i).fill(String(EXERCICIO_ATUAL));
    await pageLancador.getByLabel(/classificação orçamentária/i).fill('raz-211');
    await pageLancador.getByLabel(/fonte de recurso/i).fill('raz-211');
    await pageLancador.getByLabel(/histórico/i).fill(historicoEmpenho);
    await pageLancador.getByRole('button', { name: /registrar empenho/i }).click();
    await expect(pageLancador.getByText(historicoEmpenho)).toBeVisible();

    // --- Liquidação (lançador, LiquidacaoPage/LiquidacaoForm — RAZ-230) ---
    const historicoLiquidacao = `Liquidação UI E2E RAZ-211 ${Date.now()}`;
    await pageLancador.goto('/execucao/liquidacoes');
    await expect(pageLancador.getByRole('heading', { name: /registrar liquidação/i })).toBeVisible();
    await pageLancador.getByLabel(/^empenho$/i).click();
    await pageLancador.getByRole('option', { name: historicoEmpenho }).click();
    await pageLancador.getByLabel(/data de competência/i).fill(hoje);
    await pageLancador.getByLabel(/^valor/i).fill(valor);
    await pageLancador.getByLabel(/tipo do documento/i).fill('nota_fiscal');
    await pageLancador.getByLabel(/número do documento/i).fill('NF-UI-211');
    await pageLancador.getByLabel(/data de emissão/i).fill(hoje);
    await pageLancador.getByLabel(/histórico/i).fill(historicoLiquidacao);
    await pageLancador.getByRole('button', { name: /registrar liquidação/i }).click();
    await expect(pageLancador.getByText('Liquidação registrada.')).toBeVisible();
    await ctxLancador.close();

    // --- Aprovação (autorizador, ator distinto — Regra 9/ADR-0023 — via
    // AprovacaoFilaPage/FilaAprovacaoList/GateAprovacaoModal, RAZ-221/RAZ-229) ---
    const ctxAutorizador = await browser.newContext({
      extraHTTPHeaders: { Authorization: `Bearer ${autorizador.jwt}` },
    });
    const pageAutorizador = await ctxAutorizador.newPage();
    await pageAutorizador.goto('/execucao/aprovacoes');
    await expect(pageAutorizador.getByRole('heading', { name: /fila de aprovação/i })).toBeVisible();

    const linha = pageAutorizador.getByRole('row').filter({ hasText: valorFormatado });
    await expect(linha).toBeVisible();
    await linha.getByRole('button', { name: /decidir/i }).click();

    const modal = pageAutorizador.getByRole('dialog', { name: /decidir liquidação/i });
    await expect(modal).toBeVisible();
    // Dinheiro decimal no modal do gate (RAZ-211): mesmo valor exato, formatado.
    await expect(modal.getByText(valorFormatado)).toBeVisible();
    await modal.getByRole('button', { name: /^aprovar$/i }).click();
    await expect(modal).not.toBeVisible();
    await ctxAutorizador.close();

    // --- Pagamento (ator distinto do lançador — ver comentário acima — via
    // PagamentoPage/PagamentoForm, RAZ-230) ---
    const ctxPagador = await browser.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${pagador.jwt}` } });
    const pagePagador = await ctxPagador.newPage();
    const historicoPagamento = `Pagamento UI E2E RAZ-211 ${Date.now()}`;

    await pagePagador.goto('/execucao/pagamentos');
    await expect(pagePagador.getByRole('heading', { name: /registrar pagamento/i })).toBeVisible();
    await pagePagador.getByLabel(/liquidação/i).click();
    await pagePagador.getByRole('option', { name: valorFormatado }).click();
    await pagePagador.getByLabel(/natureza/i).selectOption('orcamentario');
    await pagePagador.getByLabel(/data de competência/i).fill(hoje);
    await pagePagador.getByLabel(/^valor/i).fill(valor);
    await pagePagador.getByLabel(/nome do beneficiário/i).fill('Fornecedor UI E2E RAZ-211');
    await pagePagador.getByLabel(/cpf\/cnpj do beneficiário/i).fill('12345678901');
    await pagePagador.getByLabel(/histórico/i).fill(historicoPagamento);
    await pagePagador.getByRole('button', { name: /registrar pagamento/i }).click();
    await expect(pagePagador.getByText('Pagamento registrado.')).toBeVisible();

    // --- Consulta final: resumo real reflete tudo, dinheiro decimal exato ---
    await pagePagador.goto('/execucao');
    await expect(pagePagador.getByText(valorFormatado).first()).toBeVisible();
    await ctxPagador.close();
  });
});
