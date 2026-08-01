/**
 * Handlers MSW — mock explicito contra o contrato real (openapi/contrato-provisorio.yaml),
 * nao suposicao solta. Usado em dev (sem backend Spring Boot rodavel neste ambiente) e em
 * testes de componente. Replica o envelope de erro real
 * (`consulta.ErroContratoExceptionHandler`, `{codigo, mensagem, detalhes}`) e a autenticacao
 * via `Authorization: Bearer` (`consulta.SessaoAutenticadaHttpResolver`).
 */
import { http, HttpResponse, type PathParams } from 'msw';
import { somarMoney, subtrairMoney } from '../../lib/dinheiro';

const randomUUID = () => crypto.randomUUID();

const BASE = '/api/v1/entes/:enteId/execucao';
const BASE_RAZAO = '/api/v1/entes/:enteId/razao';

function erroContrato(status: number, codigo: string, mensagem: string) {
  return HttpResponse.json({ codigo, mensagem, detalhes: {} }, { status });
}

function exigirBearer(request: Request) {
  const autorizacao = request.headers.get('Authorization');
  if (!autorizacao?.startsWith('Bearer ') || autorizacao.slice('Bearer '.length).trim() === '') {
    return erroContrato(401, 'nao_autenticado', "Cabeçalho 'Authorization: Bearer <asserção gov.br>' ausente ou mal formado");
  }
  return null;
}

// Estado em memoria do mock (sessao do processo do dev-server/teste) — registro real dos
// empenhos feitos via POST, para os GETs /orcamentaria e /empenhos (RAZ-121/RAZ-199)
// refletirem os POSTs desta sessao. Mesmo shape de EmpenhoRegistradoResponse.
type EmpenhoRegistradoMock = {
  id: string;
  numeroSequencial: number;
  exercicio: number;
  tipo: string;
  credorId: string;
  valor: string;
  dataFato: string;
  historico: string;
  status: string;
};

const empenhosPorEnte = new Map<string, EmpenhoRegistradoMock[]>();

function empenhosDoEnte(enteId: string) {
  const lista = empenhosPorEnte.get(enteId) ?? [];
  empenhosPorEnte.set(enteId, lista);
  return lista;
}

let numeroSequencial = 0;

// Dotacoes fixas do mock (execucao/ExecucaoConsultaController.dotacoes, RAZ-148/ADR-0038) —
// o exercicio do item de resposta reflete o `exercicio` pedido na query (a dotacao real e
// escopada por exercicio no backend); saldoDisponivel e sempre derivado (nunca hardcoded).
const DOTACOES_BASE = [
  {
    id: '33333333-3333-4333-8333-000000000001',
    classificacaoOrcamentaria: '04.122.0001.2001.3.3.90.30',
    fonteRecurso: '0100',
    unidadeGestoraId: '11111111-1111-4111-8111-111111111111',
    valorAutorizado: '500000.00',
    valorComprometido: '120000.00',
  },
  {
    id: '33333333-3333-4333-8333-000000000002',
    classificacaoOrcamentaria: '04.122.0001.2002.3.3.90.39',
    fonteRecurso: '0100',
    unidadeGestoraId: '11111111-1111-4111-8111-111111111111',
    valorAutorizado: '250000.00',
    valorComprometido: '250000.00',
  },
  {
    id: '33333333-3333-4333-8333-000000000003',
    classificacaoOrcamentaria: '10.301.0002.2010.3.3.90.30',
    fonteRecurso: '0102',
    unidadeGestoraId: '22222222-2222-4222-8222-222222222222',
    valorAutorizado: '800000.00',
    valorComprometido: '0.00',
  },
];

// Catalogo PCASP fixo do mock (razao/CatalogoContasController, RAZ-142/ADR-0030 §6) — lista
// PLANA ordenada por codigo, hierarquia derivada client-side pelo nº de segmentos do codigo.
const CATALOGO_CONTAS = [
  {
    id: '22222222-2222-4222-8222-000000000001',
    codigo: '1',
    descricao: 'Ativo',
    naturezaInformacao: 'patrimonial',
    naturezaSaldo: 'D',
    escrituravel: false,
    contaPaiId: null,
  },
  {
    id: '22222222-2222-4222-8222-000000000002',
    codigo: '1.1',
    descricao: 'Ativo circulante',
    naturezaInformacao: 'patrimonial',
    naturezaSaldo: 'D',
    escrituravel: false,
    contaPaiId: '22222222-2222-4222-8222-000000000001',
  },
  {
    id: '22222222-2222-4222-8222-000000000003',
    codigo: '1.1.1',
    descricao: 'Caixa e equivalentes de caixa',
    naturezaInformacao: 'patrimonial',
    naturezaSaldo: 'D',
    escrituravel: true,
    contaPaiId: '22222222-2222-4222-8222-000000000002',
  },
  {
    id: '22222222-2222-4222-8222-000000000004',
    codigo: '1.1.2',
    descricao: 'Créditos a curto prazo',
    naturezaInformacao: 'patrimonial',
    naturezaSaldo: 'D',
    escrituravel: true,
    contaPaiId: '22222222-2222-4222-8222-000000000002',
  },
  {
    id: '22222222-2222-4222-8222-000000000005',
    codigo: '2',
    descricao: 'Passivo',
    naturezaInformacao: 'patrimonial',
    naturezaSaldo: 'C',
    escrituravel: false,
    contaPaiId: null,
  },
  {
    id: '22222222-2222-4222-8222-000000000006',
    codigo: '2.1',
    descricao: 'Passivo circulante',
    naturezaInformacao: 'patrimonial',
    naturezaSaldo: 'C',
    escrituravel: false,
    contaPaiId: '22222222-2222-4222-8222-000000000005',
  },
  {
    id: '22222222-2222-4222-8222-000000000007',
    codigo: '2.1.1',
    descricao: 'Obrigações trabalhistas',
    naturezaInformacao: 'patrimonial',
    naturezaSaldo: 'C',
    escrituravel: true,
    contaPaiId: '22222222-2222-4222-8222-000000000006',
  },
];

const SALDO_POR_CONTA: Record<string, string> = {
  '22222222-2222-4222-8222-000000000003': '15320.50',
  '22222222-2222-4222-8222-000000000004': '4210.00',
  '22222222-2222-4222-8222-000000000007': '9875.25',
};

// Movimentos do balancete por conta escrituravel — valores fixos (nao derivados da natureza
// da conta) porque a invariante Σdebito=Σcredito e do RAZAO como um todo (cada lancamento
// tem uma ponta debito e uma credito em contas DIFERENTES), nao de uma unica linha.
const MOVIMENTOS_POR_CONTA: Record<string, { saldoAnterior: string; movimentoDebito: string; movimentoCredito: string }> = {
  '22222222-2222-4222-8222-000000000003': { saldoAnterior: '1000.00', movimentoDebito: '500.00', movimentoCredito: '200.00' },
  '22222222-2222-4222-8222-000000000004': { saldoAnterior: '1000.00', movimentoDebito: '300.00', movimentoCredito: '100.00' },
  '22222222-2222-4222-8222-000000000007': { saldoAnterior: '1000.00', movimentoDebito: '200.00', movimentoCredito: '700.00' },
};

// Fila de aprovação fixa do mock (consulta.ExecucaoConsultaController.fila, ADR-0029 §1,
// RAZ-221/ADR-0052) — id do primeiro item usado por TRILHA_POR_LIQUIDACAO abaixo.
const FILA_APROVACAO_BASE = [
  {
    id: '44444444-4444-4444-8444-000000000001',
    empenhoId: '11111111-1111-4111-8111-000000000010',
    numeroEmpenho: 1,
    exercicioEmpenho: new Date().getFullYear(),
    credorId: '11111111-1111-4111-8111-000000000002',
    valor: '1500.00',
    dataCompetencia: '2026-01-20',
    statusAprovacao: 'pendente',
  },
  {
    id: '44444444-4444-4444-8444-000000000002',
    empenhoId: '11111111-1111-4111-8111-000000000011',
    numeroEmpenho: 2,
    exercicioEmpenho: new Date().getFullYear(),
    credorId: '11111111-1111-4111-8111-000000000003',
    valor: '820.75',
    dataCompetencia: '2026-01-22',
    statusAprovacao: 'aprovada',
  },
];

// Trilha fixa por liquidação (consulta.ExecucaoConsultaController.trilha, ADR-0029 §3) — ator
// já mascarado no mock, espelhando o boundary real (nunca CPF em claro). Tipos e `detalhes`
// espelham ConsultarTrilhaLiquidacao/AprovarPagamento (execucao-application) — RAZ-240.
const TRILHA_POR_LIQUIDACAO: Record<string, { tipo: string; ator: string; quando: string; detalhes: Record<string, string> }[]> = {
  '44444444-4444-4444-8444-000000000001': [
    { tipo: 'execucao_liquidacao_registrada', ator: '***.456.***-**', quando: '2026-01-20T10:00:00Z', detalhes: {} },
  ],
  '44444444-4444-4444-8444-000000000002': [
    { tipo: 'execucao_liquidacao_registrada', ator: '***.456.***-**', quando: '2026-01-22T09:00:00Z', detalhes: {} },
    {
      tipo: 'execucao_pagamento_aprovacao_decidida',
      ator: '***.789.***-**',
      quando: '2026-01-22T14:30:00Z',
      detalhes: { decisao: 'APROVADA', empenhoId: '11111111-1111-4111-8111-000000000011' },
    },
  ],
};

export const handlers = [
  // GET /sessao/atual (RAZ-203/RAZ-205) — fora do template /api/v1/entes/:enteId. Neste
  // ambiente de mock nao ha cookie de sessao do BFF de login real (ADR-0035) pra simular, so
  // o form de dev (que nunca chama este endpoint, so seta a sessao local) — 401 sempre,
  // fiel ao caso real "sem cookie/bearer valido" que AuthProvider trata como "nao logado".
  http.get('/sessao/atual', () => {
    return erroContrato(401, 'nao_autenticado', 'Nenhuma sessão de login (cookie) válida.');
  }),

  http.post(`${BASE}/empenhos`, async ({ request, params }) => {
    const erro = exigirBearer(request);
    if (erro) return erro;
    const { enteId } = params as PathParams;
    const body = (await request.json()) as Record<string, unknown>;

    if (typeof body.dotacaoId !== 'string' || typeof body.credorId !== 'string' || typeof body.unidadeGestoraId !== 'string') {
      return erroContrato(400, 'requisicao_invalida', 'payload inválido para registrar empenho');
    }

    numeroSequencial += 1;
    const empenhoId = randomUUID();
    empenhosDoEnte(String(enteId)).unshift({
      id: empenhoId,
      numeroSequencial,
      exercicio: Number(body.exercicio),
      tipo: String(body.tipo),
      credorId: String(body.credorId),
      valor: String(body.valor),
      dataFato: String(body.dataFato),
      historico: String(body.historico),
      status: 'registrado',
    });

    return HttpResponse.json(
      {
        id: empenhoId,
        numeroSequencial,
        exercicio: body.exercicio,
        tipo: body.tipo,
        dotacaoId: body.dotacaoId,
        credorId: body.credorId,
        unidadeGestoraId: body.unidadeGestoraId,
        contratoId: body.contratoId ?? null,
        valor: String(body.valor),
        dataFato: body.dataFato,
        classificacaoOrcamentaria: body.classificacaoOrcamentaria,
        fonteRecurso: body.fonteRecurso,
        historico: body.historico,
        fatoContabilId: randomUUID(),
      },
      { status: 201 },
    );
  }),

  http.get(`${BASE}/empenhos`, ({ request, params }) => {
    const erro = exigirBearer(request);
    if (erro) return erro;
    const { enteId } = params as PathParams;
    const url = new URL(request.url);
    const exercicioParam = url.searchParams.get('exercicio');
    const cursor = url.searchParams.get('cursor');
    const limit = Math.min(Number(url.searchParams.get('limit')) || 20, 100);

    const todos = empenhosDoEnte(String(enteId));
    const filtrados = exercicioParam ? todos.filter((e) => e.exercicio === Number(exercicioParam)) : todos;

    const offset = cursor ? Number(cursor) : 0;
    const pagina = filtrados.slice(offset, offset + limit);
    const proximoOffset = offset + pagina.length;
    const proximoCursor = proximoOffset < filtrados.length ? String(proximoOffset) : null;

    return HttpResponse.json({ itens: pagina, proximoCursor });
  }),

  http.post(`${BASE}/liquidacoes`, async ({ request }) => {
    const erro = exigirBearer(request);
    if (erro) return erro;
    const body = (await request.json()) as Record<string, unknown>;
    return HttpResponse.json(
      {
        id: randomUUID(),
        empenhoId: body.empenhoId,
        dataCompetencia: body.dataCompetencia,
        valor: String(body.valor),
        documentosSuporte: body.documentosSuporte ?? [],
        historico: body.historico,
        fatoContabilId: randomUUID(),
        status: 'pendente',
      },
      { status: 201 },
    );
  }),

  // Fila de aprovação (gate 4-eyes, RAZ-221/ADR-0052) — itens fixos do mock, mesmo padrão de
  // DOTACOES_BASE/CATALOGO_CONTAS acima (não amarrado ao estado do POST /liquidacoes, que
  // ainda não tem armazenamento em memória como empenhosPorEnte).
  http.get(`${BASE}/liquidacoes`, ({ request }) => {
    const erro = exigirBearer(request);
    if (erro) return erro;
    const url = new URL(request.url);
    const statusAprovacao = url.searchParams.get('statusAprovacao') ?? 'pendente';
    const itens = FILA_APROVACAO_BASE.filter((item) => item.statusAprovacao === statusAprovacao);
    return HttpResponse.json({ itens, proximoCursor: null });
  }),

  http.get(`${BASE}/liquidacoes/:id/trilha`, ({ request, params }) => {
    const erro = exigirBearer(request);
    if (erro) return erro;
    const { id } = params as PathParams;
    return HttpResponse.json({
      liquidacaoId: id,
      eventos: TRILHA_POR_LIQUIDACAO[String(id)] ?? [],
    });
  }),

  // Decisão do gate 4-eyes (ADR-0055) — feliz padrão contra um item fixo de FILA_APROVACAO_BASE;
  // os testes de erro (409/403/428/400 do mapeamento da decisão 4) sobrescrevem via `server.use`,
  // mesmo padrão de LiquidacaoForm.test.tsx pro POST /liquidacoes.
  http.post(`${BASE}/liquidacoes/:id/aprovacao`, async ({ request, params }) => {
    const erro = exigirBearer(request);
    if (erro) return erro;
    const { id } = params as PathParams;
    const body = (await request.json()) as Record<string, unknown>;
    const item = FILA_APROVACAO_BASE.find((i) => i.id === id);
    if (!item) {
      return erroContrato(400, 'liquidacao_nao_encontrada', 'Liquidação não encontrada.');
    }
    return HttpResponse.json({
      id: item.id,
      empenhoId: item.empenhoId,
      dataCompetencia: item.dataCompetencia,
      valor: item.valor,
      documentosSuporte: [],
      historico: 'Liquidação de mock',
      fatoContabilId: randomUUID(),
      status: body.decisao === 'devolver' ? 'devolvida' : 'aprovada',
    });
  }),

  http.post(`${BASE}/pagamentos`, async ({ request }) => {
    const erro = exigirBearer(request);
    if (erro) return erro;
    const body = (await request.json()) as Record<string, unknown>;
    return HttpResponse.json(
      {
        id: randomUUID(),
        liquidacaoId: body.liquidacaoId,
        dataCompetencia: body.dataCompetencia,
        valor: String(body.valor),
        natureza: body.natureza,
        beneficiario: body.beneficiario ?? null,
        ordemBancaria: body.ordemBancaria ?? null,
        historico: body.historico,
        fatoContabilId: randomUUID(),
      },
      { status: 201 },
    );
  }),

  http.get(`${BASE}/orcamentaria`, ({ request, params }) => {
    const erro = exigirBearer(request);
    if (erro) return erro;
    const { enteId } = params as PathParams;
    const url = new URL(request.url);
    const exercicio = Number(url.searchParams.get('exercicio'));
    const mes = Number(url.searchParams.get('mes'));

    const totalEmpenhado = somarMoney(
      ...empenhosDoEnte(String(enteId))
        .filter((e) => e.exercicio === exercicio)
        .map((e) => e.valor),
    );

    return HttpResponse.json({
      exercicio,
      mes,
      totalEmpenhado,
      totalLiquidado: '0.00',
      totalPago: '0.00',
      saldoALiquidar: totalEmpenhado,
      saldoAPagar: '0.00',
    });
  }),

  http.get(`${BASE}/dotacoes`, ({ request }) => {
    const erro = exigirBearer(request);
    if (erro) return erro;
    const url = new URL(request.url);
    const exercicio = Number(url.searchParams.get('exercicio'));
    if (!exercicio) {
      return erroContrato(400, 'requisicao_invalida', "parâmetro 'exercicio' obrigatório");
    }
    const busca = (url.searchParams.get('busca') ?? '').trim().toLowerCase();
    const cursor = url.searchParams.get('cursor');
    const limit = Math.min(Number(url.searchParams.get('limit')) || 20, 100);

    const itensDoExercicio = DOTACOES_BASE.map((dotacao) => ({
      ...dotacao,
      exercicio,
      saldoDisponivel: subtrairMoney(dotacao.valorAutorizado, dotacao.valorComprometido),
    }));

    const filtradas = busca
      ? itensDoExercicio.filter(
          (d) =>
            d.classificacaoOrcamentaria.toLowerCase().includes(busca) ||
            d.fonteRecurso.toLowerCase().includes(busca),
        )
      : itensDoExercicio;

    const offset = cursor ? Number(cursor) : 0;
    const pagina = filtradas.slice(offset, offset + limit);
    const proximoOffset = offset + pagina.length;
    const proximoCursor = proximoOffset < filtradas.length ? String(proximoOffset) : null;

    return HttpResponse.json({ itens: pagina, proximoCursor });
  }),

  http.get(`${BASE_RAZAO}/saldo`, ({ request }) => {
    const erro = exigirBearer(request);
    if (erro) return erro;
    const url = new URL(request.url);
    const contaId = url.searchParams.get('contaId');
    const conta = CATALOGO_CONTAS.find((c) => c.id === contaId);
    if (!conta) {
      return erroContrato(404, 'conta_nao_encontrada', 'Conta não encontrada para o ente informado.');
    }
    return HttpResponse.json({
      contaId: conta.id,
      saldo: SALDO_POR_CONTA[conta.id] ?? '0.00',
    });
  }),

  http.get(`${BASE_RAZAO}/balancete`, ({ request }) => {
    const erro = exigirBearer(request);
    if (erro) return erro;
    const url = new URL(request.url);
    const exercicio = Number(url.searchParams.get('exercicio'));
    const mes = Number(url.searchParams.get('mes'));

    const contasEscrituraveis = CATALOGO_CONTAS.filter((c) => c.escrituravel);
    const linhas = contasEscrituraveis.map((conta) => {
      const { saldoAnterior, movimentoDebito, movimentoCredito } = MOVIMENTOS_POR_CONTA[conta.id] ?? {
        saldoAnterior: '0.00',
        movimentoDebito: '0.00',
        movimentoCredito: '0.00',
      };
      const saldoAtual =
        conta.naturezaSaldo === 'D'
          ? subtrairMoney(somarMoney(saldoAnterior, movimentoDebito), movimentoCredito)
          : subtrairMoney(somarMoney(saldoAnterior, movimentoCredito), movimentoDebito);
      return {
        contaId: conta.id,
        codigo: conta.codigo,
        descricao: conta.descricao,
        naturezaSaldo: conta.naturezaSaldo,
        saldoAnterior,
        movimentoDebito,
        movimentoCredito,
        saldoAtual,
      };
    });

    const totalMovimentoDebito = somarMoney(...linhas.map((l) => l.movimentoDebito));
    const totalMovimentoCredito = somarMoney(...linhas.map((l) => l.movimentoCredito));

    return HttpResponse.json({
      exercicio,
      mes,
      linhas,
      totalMovimentoDebito,
      totalMovimentoCredito,
      confere: totalMovimentoDebito === totalMovimentoCredito,
    });
  }),

  http.get(`${BASE_RAZAO}/contas`, ({ request }) => {
    const erro = exigirBearer(request);
    if (erro) return erro;
    const url = new URL(request.url);
    const busca = (url.searchParams.get('busca') ?? '').trim().toLowerCase();
    const cursor = url.searchParams.get('cursor');
    const limit = Math.min(Number(url.searchParams.get('limit')) || 20, 100);

    const filtradas = busca
      ? CATALOGO_CONTAS.filter(
          (c) => c.codigo.startsWith(busca) || c.descricao.toLowerCase().includes(busca),
        )
      : CATALOGO_CONTAS;

    const offset = cursor ? Number(cursor) : 0;
    const pagina = filtradas.slice(offset, offset + limit);
    const proximoOffset = offset + pagina.length;
    const proximoCursor = proximoOffset < filtradas.length ? String(proximoOffset) : null;

    return HttpResponse.json({
      itens: pagina,
      proximoCursor,
    });
  }),
];
