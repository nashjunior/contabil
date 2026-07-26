/**
 * Client de API — camada fina e estavel sobre o contrato gerado (ADR-0033/ADR-0030).
 * Features importam DAQUI, nunca de `shared/api/generated/` diretamente.
 *
 * Contrato REAL (nao mais o consolidado divergente de uma copia de workspace desconectada):
 * enteId no PATH (`/api/v1/entes/{enteId}/...`, ADR-0015) + um unico header
 * `Authorization: Bearer <assercao gov.br>`, verificado a cada request (stateless) por
 * `ServicoIdentidade` — ver java: bootstrap/.../consulta/SessaoAutenticadaHttpResolver.java.
 */
import { parseJsonPreservingMoney, stringifyBodyWithRawMoney } from '../lib/moneyJson';
import type { components } from './generated/schema';

export type GovbrContexto = {
  /** Assercao gov.br (bearer token) — opaca para o cliente, nunca decodificada aqui. */
  bearerToken: string;
  enteId: string;
};

export class ApiError extends Error {
  readonly status: number;
  readonly codigo: string;
  readonly detalhes: Record<string, string>;

  constructor(status: number, codigo: string, message: string, detalhes: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.codigo = codigo;
    this.detalhes = detalhes;
  }
}

const API_ORIGIN = import.meta.env.VITE_API_BASE_URL ?? '';

function baseUrl(enteId: string): string {
  return `${API_ORIGIN}/api/v1/entes/${enteId}`;
}

function headersFor(contexto: GovbrContexto): HeadersInit {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${contexto.bearerToken}`,
  };
}

async function tratarResposta<TResponse>(response: Response): Promise<TResponse> {
  const rawText = await response.text();
  const parsed = rawText ? parseJsonPreservingMoney(rawText) : null;

  if (!response.ok) {
    const erro = parsed as components['schemas']['ErroContratoResponse'] | null;
    throw new ApiError(
      response.status,
      erro?.codigo ?? 'erro_desconhecido',
      erro?.mensagem ?? 'Falha ao comunicar com a API.',
      erro?.detalhes ?? {},
    );
  }

  return parsed as TResponse;
}

async function post<TResponse>(path: string, body: Record<string, unknown>, contexto: GovbrContexto): Promise<TResponse> {
  const response = await fetch(`${baseUrl(contexto.enteId)}${path}`, {
    method: 'POST',
    headers: headersFor(contexto),
    body: stringifyBodyWithRawMoney(body),
  });
  return tratarResposta<TResponse>(response);
}

async function get<TResponse>(path: string, query: Record<string, string | number>, contexto: GovbrContexto): Promise<TResponse> {
  const queryString = new URLSearchParams(
    Object.fromEntries(Object.entries(query).map(([k, v]) => [k, String(v)])),
  ).toString();
  const response = await fetch(`${baseUrl(contexto.enteId)}${path}?${queryString}`, {
    method: 'GET',
    headers: headersFor(contexto),
  });
  return tratarResposta<TResponse>(response);
}

export type EmpenhoRequest = components['schemas']['EmpenhoRequest'];
export type EmpenhoResponse = components['schemas']['EmpenhoResponse'];
export type LiquidacaoRequest = components['schemas']['LiquidacaoRequest'];
export type LiquidacaoResponse = components['schemas']['LiquidacaoResponse'];
export type PagamentoRequest = components['schemas']['PagamentoRequest'];
export type PagamentoResponse = components['schemas']['PagamentoResponse'];
export type ExecucaoOrcamentariaResponse = components['schemas']['ExecucaoOrcamentariaResponse'];

export const execucaoClient = {
  registrarEmpenho: (body: EmpenhoRequest, contexto: GovbrContexto) =>
    post<EmpenhoResponse>('/execucao/empenhos', body, contexto),
  registrarLiquidacao: (body: LiquidacaoRequest, contexto: GovbrContexto) =>
    post<LiquidacaoResponse>('/execucao/liquidacoes', body, contexto),
  registrarPagamento: (body: PagamentoRequest, contexto: GovbrContexto) =>
    post<PagamentoResponse>('/execucao/pagamentos', body, contexto),
  consultarExecucaoOrcamentaria: (exercicio: number, mes: number, contexto: GovbrContexto) =>
    get<ExecucaoOrcamentariaResponse>('/execucao/orcamentaria', { exercicio, mes }, contexto),
};
