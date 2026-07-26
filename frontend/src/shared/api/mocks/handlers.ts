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
];
