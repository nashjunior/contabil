/**
 * Handlers MSW — mock explicito contra o contrato real (openapi/contrato-provisorio.yaml),
 * nao suposicao solta. Usado em dev (sem backend Spring Boot rodavel neste ambiente) e em
 * testes de componente. Replica o envelope de erro real
 * (`consulta.ErroContratoExceptionHandler`, `{codigo, mensagem, detalhes}`) e a autenticacao
 * via `Authorization: Bearer` (`consulta.SessaoAutenticadaHttpResolver`).
 */
import { http, HttpResponse, type PathParams } from 'msw';

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

// Estado em memoria do mock (sessao do processo do dev-server/teste) — soma real dos empenhos
// registrados, para o GET /orcamentaria refletir os POSTs feitos nesta sessao.
const empenhosPorEnte = new Map<string, Array<{ valor: string; exercicio: number }>>();

function empenhosDoEnte(enteId: string) {
  const lista = empenhosPorEnte.get(enteId) ?? [];
  empenhosPorEnte.set(enteId, lista);
  return lista;
}

let numeroSequencial = 0;

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

export const handlers = [
  http.post(`${BASE}/empenhos`, async ({ request, params }) => {
    const erro = exigirBearer(request);
    if (erro) return erro;
    const { enteId } = params as PathParams;
    const body = (await request.json()) as Record<string, unknown>;

    if (typeof body.dotacaoId !== 'string' || typeof body.credorId !== 'string' || typeof body.unidadeGestoraId !== 'string') {
      return erroContrato(400, 'requisicao_invalida', 'payload inválido para registrar empenho');
    }

    numeroSequencial += 1;
    empenhosDoEnte(String(enteId)).push({ valor: String(body.valor), exercicio: Number(body.exercicio) });

    return HttpResponse.json(
      {
        id: randomUUID(),
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

    const totalEmpenhado = empenhosDoEnte(String(enteId))
      .filter((e) => e.exercicio === exercicio)
      .reduce((soma, e) => soma + Number(e.valor), 0);

    return HttpResponse.json({
      exercicio,
      mes,
      totalEmpenhado: totalEmpenhado.toFixed(2),
      totalLiquidado: '0.00',
      totalPago: '0.00',
      saldoALiquidar: totalEmpenhado.toFixed(2),
      saldoAPagar: '0.00',
    });
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
          ? (Number(saldoAnterior) + Number(movimentoDebito) - Number(movimentoCredito)).toFixed(2)
          : (Number(saldoAnterior) + Number(movimentoCredito) - Number(movimentoDebito)).toFixed(2);
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

    const totalMovimentoDebito = linhas
      .reduce((soma, l) => soma + Number(l.movimentoDebito), 0)
      .toFixed(2);
    const totalMovimentoCredito = linhas
      .reduce((soma, l) => soma + Number(l.movimentoCredito), 0)
      .toFixed(2);

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
