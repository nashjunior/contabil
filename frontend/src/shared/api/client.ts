/**
 * Client de API — camada fina e estavel sobre o contrato gerado (ADR-0033/ADR-0030).
 * Features importam DAQUI, nunca de `shared/api/generated/` diretamente.
 *
 * Contrato REAL (nao mais o consolidado divergente de uma copia de workspace desconectada):
 * enteId no PATH (`/api/v1/entes/{enteId}/...`, ADR-0015) + `Authorization: Bearer
 * <assercao gov.br>`, verificado a cada request (stateless) por `ServicoIdentidade` — ver
 * java: bootstrap/.../consulta/SessaoAutenticadaHttpResolver.java.
 *
 * Caminho por cookie (ADR-0035, RAZ-199): quando a sessao vem do BFF de login real
 * (`/sessao/oauth/*`), o navegador nunca tem a assercao (`bearerToken` ausente) — o
 * `SessaoAutenticadaHttpResolver` cai no fallback por cookie de sessao. `credentials:
 * 'include'` garante que o cookie de sessao viaje na chamada; toda chamada mutante nesse
 * modo ecoa o cookie anti-CSRF (`sessao-csrf`) no cabecalho `X-Csrf-Token`
 * (`CsrfCookieProtecaoFilter`, falha fechado sem ele).
 */
import { parseJsonPreservingMoney, stringifyBodyWithRawMoney } from '../lib/moneyJson';
import type { components } from './generated/schema';

export type GovbrContexto = {
  /**
   * Assercao gov.br (bearer token) — opaca para o cliente, nunca decodificada aqui.
   * Ausente quando a sessao ativa veio do cookie do BFF de login real (ADR-0035): nesse
   * caso a API le a assercao guardada server-side, e a chamada precisa ir por cookie +
   * CSRF em vez de `Authorization`.
   */
  bearerToken?: string;
  enteId: string;
};

const CSRF_COOKIE_NAME = 'sessao-csrf';
const CSRF_HEADER = 'X-Csrf-Token';
const METODOS_MUTANTES = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/** java: sessao.CsrfCookieSessaoLogin — cookie legivel por JS (nao HttpOnly), so um nonce. */
function csrfTokenDoCookie(): string | undefined {
  if (typeof document === 'undefined') return undefined;
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : undefined;
}

/** Toda chamada aceita `signal` e repassa ao `fetch` (ADR-0041/RAZ-137): hook de
 * query encaminha o signal do React Query, caso de uso/busca imperativa gerencia
 * seu próprio `AbortController`. */
export type RequestOptions = {
  signal?: AbortSignal;
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

/** Origem da API (`VITE_API_BASE_URL`) — exportado para montar links fora do client de
 * dados (ex.: o `<a href>` de `/sessao/oauth/iniciar`, uma navegação de página inteira,
 * não uma chamada `fetch`, ver `shared/auth/LoginPage.tsx`). */
export const apiOrigin = API_ORIGIN;

function baseUrl(enteId: string): string {
  return `${API_ORIGIN}/api/v1/entes/${enteId}`;
}

function headersFor(contexto: GovbrContexto, method: string): HeadersInit {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (contexto.bearerToken) {
    headers.Authorization = `Bearer ${contexto.bearerToken}`;
    return headers;
  }
  if (METODOS_MUTANTES.has(method)) {
    const csrf = csrfTokenDoCookie();
    if (csrf) headers[CSRF_HEADER] = csrf;
  }
  return headers;
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

type ObservadorSessaoExpirada = () => void;
let observadorSessaoExpirada: ObservadorSessaoExpirada | null = null;

/**
 * Registra o observador de expiração de sessão (RAZ-239). `AuthProvider` (React) o registra
 * para saber quando uma chamada AUTENTICADA (não a hidratação `sessaoClient.atual`) devolve
 * 401 — sinal de que a sessão que existia expirou/foi revogada no meio do uso, distinto de
 * "nunca logou". client.ts permanece agnóstico de React: só guarda o callback. `null`
 * desregistra (cleanup do efeito).
 */
export function registrarObservadorSessaoExpirada(observador: ObservadorSessaoExpirada | null): void {
  observadorSessaoExpirada = observador;
}

/**
 * Trata a resposta de uma chamada autenticada (`get`/`post`, sempre com `GovbrContexto` de
 * sessão ativa — `useGovbrContexto` lança sem sessão). Um 401 aqui NÃO é "nunca logou" (isso
 * é a hidratação `sessaoClient.atual`, que chama `tratarResposta` direto): é uma sessão que
 * caiu no meio do uso — avisa o observador antes de propagar o `ApiError` normal ao chamador.
 */
async function tratarRespostaAutenticada<TResponse>(response: Response): Promise<TResponse> {
  if (response.status === 401) observadorSessaoExpirada?.();
  return tratarResposta<TResponse>(response);
}

async function post<TResponse>(
  path: string,
  body: Record<string, unknown>,
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<TResponse> {
  const response = await fetch(`${baseUrl(contexto.enteId)}${path}`, {
    method: 'POST',
    headers: headersFor(contexto, 'POST'),
    body: stringifyBodyWithRawMoney(body),
    credentials: 'include',
    signal: options?.signal,
  });
  return tratarRespostaAutenticada<TResponse>(response);
}

async function get<TResponse>(
  path: string,
  query: Record<string, string | number>,
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<TResponse> {
  const queryString = new URLSearchParams(
    Object.fromEntries(Object.entries(query).map(([k, v]) => [k, String(v)])),
  ).toString();
  const response = await fetch(`${baseUrl(contexto.enteId)}${path}?${queryString}`, {
    method: 'GET',
    headers: headersFor(contexto, 'GET'),
    credentials: 'include',
    signal: options?.signal,
  });
  return tratarRespostaAutenticada<TResponse>(response);
}

export type SessaoAtualResponse = components['schemas']['SessaoAtualResponse'];

export type SessaoDevIdpRequest = { cpf: string; enteId: string };
export type SessaoDevIdpResponse = { bearerToken: string };

/** Erro de `POST /sessao/dev-idp/token` (RAZ-228) — envelope próprio (`{erro}`), distinto do
 * `ErroContratoResponse` (`{codigo, mensagem, detalhes}`) das rotas de negócio: este endpoint é
 * dev-only e não passa por `ErroContratoExceptionHandler`, ver `SessaoDevIdpController.java`.
 * `erro: 'endpoint_indisponivel'` é sintético (não vem do backend) — cobre o 404 fail-closed de
 * `ConditionalOnDevIdpAtivo` (backend real sem as duas flags de dev-IdP ligadas), cujo corpo não
 * é `{erro}` (é a página de erro default do Spring), então não tenta parsear como JSON de erro. */
export class DevIdpTokenError extends Error {
  readonly erro: string;
  readonly status: number;

  constructor(erro: string, status: number) {
    super(erro);
    this.name = 'DevIdpTokenError';
    this.erro = erro;
    this.status = status;
  }
}

/**
 * GET /sessao/atual (RAZ-203/RAZ-205) — fora do template `/api/v1/entes/{enteId}`, igual
 * aos endpoints de assinatura: é justamente o dado que este endpoint descobre. Autenticação
 * por cookie de sessão do BFF de login (`credentials: 'include'`) — usado por `AuthProvider`
 * para hidratar a sessão no mount/depois do redirect do gov.br (`shared/auth/AuthContext.tsx`).
 * 401 (`ApiError`) é o caso esperado de "sem sessão", não uma falha de rede — quem chama trata.
 */
export const sessaoClient = {
  atual: (options?: RequestOptions) =>
    fetch(`${API_ORIGIN}/sessao/atual`, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      signal: options?.signal,
    }).then((response) => tratarResposta<SessaoAtualResponse>(response)),
  /** POST /sessao/dev-idp/token (RAZ-228/ADR-0052 item 1) — substitui a string opaca que o
   * formulário "Modo desenvolvimento" fabricava por um JWT RS256 real, assinado pelo backend
   * (`AssinadorJwtDevIdp`). Sem `credentials: 'include'`: stateless, não depende do cookie do
   * BFF (ADR-0035) — a rota só existe (404 senão) quando o backend roda com as duas flags de
   * dev-IdP ativas, ver `SessaoDevIdpController.java`. */
  emitirTokenDev: async (body: SessaoDevIdpRequest, options?: RequestOptions): Promise<SessaoDevIdpResponse> => {
    const response = await fetch(`${API_ORIGIN}/sessao/dev-idp/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: options?.signal,
    });
    if (response.status === 404) {
      throw new DevIdpTokenError('endpoint_indisponivel', 404);
    }
    const parsed = (await response.json()) as SessaoDevIdpResponse | { erro?: string };
    if (!response.ok) {
      throw new DevIdpTokenError('erro' in parsed && parsed.erro ? parsed.erro : 'erro_desconhecido', response.status);
    }
    return parsed as SessaoDevIdpResponse;
  },
};

export type EmpenhoRequest = components['schemas']['EmpenhoRequest'];
export type EmpenhoResponse = components['schemas']['EmpenhoResponse'];
export type LiquidacaoRequest = components['schemas']['LiquidacaoRequest'];
export type LiquidacaoResponse = components['schemas']['LiquidacaoResponse'];
export type AprovacaoRequest = components['schemas']['AprovacaoRequest'];
export type PagamentoRequest = components['schemas']['PagamentoRequest'];
export type PagamentoResponse = components['schemas']['PagamentoResponse'];
export type ExecucaoOrcamentariaResponse = components['schemas']['ExecucaoOrcamentariaResponse'];
export type DotacaoResponse = components['schemas']['DotacaoResponse'];
export type DotacoesResponse = components['schemas']['DotacoesResponse'];
export type EmpenhoRegistradoResponse = components['schemas']['EmpenhoRegistradoResponse'];
export type EmpenhosRegistradosResponse = components['schemas']['EmpenhosRegistradosResponse'];

export const execucaoClient = {
  registrarEmpenho: (body: EmpenhoRequest, contexto: GovbrContexto, options?: RequestOptions) =>
    post<EmpenhoResponse>('/execucao/empenhos', body, contexto, options),
  consultarEmpenhosRegistrados: (
    params: { exercicio?: number; dataInicio?: string; dataFim?: string; cursor?: string; limit?: number },
    contexto: GovbrContexto,
    options?: RequestOptions,
  ) => {
    const query: Record<string, string | number> = {};
    if (params.exercicio != null) query['exercicio'] = params.exercicio;
    if (params.dataInicio) query['dataInicio'] = params.dataInicio;
    if (params.dataFim) query['dataFim'] = params.dataFim;
    if (params.cursor) query['cursor'] = params.cursor;
    if (params.limit != null) query['limit'] = params.limit;
    return get<EmpenhosRegistradosResponse>('/execucao/empenhos', query, contexto, options);
  },
  registrarLiquidacao: (body: LiquidacaoRequest, contexto: GovbrContexto, options?: RequestOptions) =>
    post<LiquidacaoResponse>('/execucao/liquidacoes', body, contexto, options),
  /** Decisão do gate 4-eyes (RAZ-221/ADR-0052) — java: escrita.LiquidacaoController.aprovar. */
  aprovarLiquidacao: (
    liquidacaoId: string,
    body: AprovacaoRequest,
    contexto: GovbrContexto,
    options?: RequestOptions,
  ) => post<LiquidacaoResponse>(`/execucao/liquidacoes/${liquidacaoId}/aprovacao`, body, contexto, options),
  registrarPagamento: (body: PagamentoRequest, contexto: GovbrContexto, options?: RequestOptions) =>
    post<PagamentoResponse>('/execucao/pagamentos', body, contexto, options),
  consultarExecucaoOrcamentaria: (exercicio: number, mes: number, contexto: GovbrContexto, options?: RequestOptions) =>
    get<ExecucaoOrcamentariaResponse>('/execucao/orcamentaria', { exercicio, mes }, contexto, options),
  consultarDotacoes: (
    params: { exercicio: number; busca?: string; cursor?: string; limit?: number },
    contexto: GovbrContexto,
    options?: RequestOptions,
  ) => {
    const query: Record<string, string | number> = { exercicio: params.exercicio };
    if (params.busca) query['busca'] = params.busca;
    if (params.cursor) query['cursor'] = params.cursor;
    if (params.limit != null) query['limit'] = params.limit;
    return get<DotacoesResponse>('/execucao/dotacoes', query, contexto, options);
  },
  consultarFilaAprovacao: (
    params: {
      statusAprovacao?: 'pendente' | 'aprovada' | 'devolvida';
      cursor?: string;
      limit?: number;
      fonte?: string;
      dataInicio?: string;
      dataFim?: string;
      valorMin?: string;
      valorMax?: string;
    },
    contexto: GovbrContexto,
    options?: RequestOptions,
  ) => {
    const query: Record<string, string | number> = {};
    if (params.statusAprovacao) query['statusAprovacao'] = params.statusAprovacao;
    if (params.cursor) query['cursor'] = params.cursor;
    if (params.limit != null) query['limit'] = params.limit;
    if (params.fonte) query['fonte'] = params.fonte;
    if (params.dataInicio) query['dataInicio'] = params.dataInicio;
    if (params.dataFim) query['dataFim'] = params.dataFim;
    if (params.valorMin) query['valorMin'] = params.valorMin;
    if (params.valorMax) query['valorMax'] = params.valorMax;
    return get<FilaAprovacaoResponse>('/execucao/liquidacoes', query, contexto, options);
  },
  consultarTrilhaLiquidacao: (liquidacaoId: string, contexto: GovbrContexto, options?: RequestOptions) =>
    get<TrilhaResponse>(`/execucao/liquidacoes/${liquidacaoId}/trilha`, {}, contexto, options),
};

export type SaldoContaResponse = components['schemas']['SaldoContaResponse'];
export type BalanceteResponse = components['schemas']['BalanceteResponse'];
export type LinhaBalancete = components['schemas']['LinhaBalancete'];
export type ContaResumo = components['schemas']['ContaResumo'];
export type ContasResponse = components['schemas']['ContasResponse'];
export type FilaAprovacaoResponse = components['schemas']['FilaAprovacaoResponse'];
export type ItemFilaAprovacao = components['schemas']['ItemFilaAprovacao'];
export type TrilhaResponse = components['schemas']['TrilhaResponse'];
export type EventoTrilha = components['schemas']['EventoTrilha'];

export const razaoClient = {
  consultarSaldo: (contaId: string, contexto: GovbrContexto, options?: RequestOptions) =>
    get<SaldoContaResponse>('/razao/saldo', { contaId }, contexto, options),
  consultarBalancete: (exercicio: number, mes: number, contexto: GovbrContexto, options?: RequestOptions) =>
    get<BalanceteResponse>('/razao/balancete', { exercicio, mes }, contexto, options),
  consultarContas: (
    params: { busca?: string; cursor?: string; limit?: number },
    contexto: GovbrContexto,
    options?: RequestOptions,
  ) => {
    const query: Record<string, string | number> = {};
    if (params.busca) query['busca'] = params.busca;
    if (params.cursor) query['cursor'] = params.cursor;
    if (params.limit != null) query['limit'] = params.limit;
    return get<ContasResponse>('/razao/contas', query, contexto, options);
  },
};
