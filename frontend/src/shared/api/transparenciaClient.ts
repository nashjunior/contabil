/**
 * Client de API do Portal Público de Transparência (RAZ-278/ADR-0060, contrato real de
 * RAZ-273/ADR-0059: `TransparenciaPublicaController`). Deliberadamente SEPARADO de
 * `client.ts`: essa superfície não tem sessão (sem `GovbrContexto`, sem `Authorization`/
 * cookie/CSRF), o `enteId` vem do path (`/transparencia/{enteId}/...`), não do template
 * `/api/v1/entes/{enteId}` do back-office. Tipos abaixo são escritos à mão (o contrato ainda
 * não está em `openapi/contrato-provisorio.yaml` — mesma situação que `execucaoClient`/
 * `razaoClient` tiveram até seus endpoints entrarem no schema gerado, ver histórico em
 * `client.ts`), espelhando 1:1 os records de `TransparenciaPublicaController.java`.
 */
import { parseJsonPreservingMoney } from '../lib/moneyJson';
import { apiOrigin, ApiError, type RequestOptions } from './client';

/** `TransparenciaPayloadPublico.estagioDe` — só os 3 valores realmente publicados hoje
 * (`receita` está reservado no schema/constraint do banco, mas nenhum produtor emite ainda,
 * `desconhecido` é fallback defensivo do backend). */
export type EstagioTransparencia = 'empenhado' | 'liquidado' | 'pago' | 'receita' | 'desconhecido';

/** `payload` é o `JsonNode` sanitizado (`TransparenciaPayloadPublico.sanitizar`) — formato
 * varia por `tipoEvento`/`estagio` (ver `features/transparencia/domain/payloadDespesa.ts`,
 * que centraliza a leitura tolerante desses campos). Nunca tipar como um shape fixo aqui:
 * seria inventar uma união de campos que o backend não garante. */
export type PayloadDespesaPublica = Record<string, unknown>;

export type ItemTransparencia = {
  tipoEvento: string;
  recurso: string;
  sequencia: number;
  publicadoEm: string;
  publicarAte: string;
  payload: PayloadDespesaPublica;
};

export type OrdenacaoTransparencia = {
  campo: string;
  direcao: string;
  padraoDocumentado: string;
};

export type DespesasPublicasResponse = {
  itens: ItemTransparencia[];
  proximoCursor: string | null;
  haMais: boolean;
  contagemAproximada: number;
  ultimaAtualizacao: string | null;
  parametros: Record<string, string>;
  ordenacao: OrdenacaoTransparencia;
  camposOrdenaveis: string[];
};

export type TotalizacaoLinha = {
  estagio: string;
  valor: string;
  quantidade: number;
};

export type TotalizacaoTransparenciaResponse = {
  linhas: TotalizacaoLinha[];
  totalGeral: string;
  contagemAproximada: number;
  ultimaAtualizacao: string | null;
  parametros: Record<string, string>;
};

export type BulkManifestResponse = {
  formato: string;
  versao: string;
  arquivoCdn: string;
  contagemAproximada: number;
  ultimaAtualizacao: string | null;
  parametros: Record<string, string>;
};

/** Espelha os parâmetros de `FiltroTransparenciaPublica` (`despesas`/`totalizacoes`/`bulk`
 * aceitam o mesmo conjunto — UX-2/ADR-0059). `ordenarPor`/`direcao`/`cursor`/`limit` só fazem
 * sentido em `despesas` (paginação keyset); os demais endpoints ignoram esses 4 campos porque
 * a API os ignora (não porque o frontend os omite por conta própria). */
export type FiltroDespesasPublicas = {
  estagio?: string;
  credorId?: string;
  orgaoId?: string;
  dataInicio?: string;
  dataFim?: string;
  funcao?: string;
  numeroEmpenho?: string;
  contratoId?: string;
  ordenarPor?: string;
  direcao?: string;
  cursor?: string;
  limit?: number;
};

/** Campos monetários que chegam como NÚMERO JSON cru nas respostas de totalização
 * (`TotalizacaoTransparenciaPublica`/`Linha` são `BigDecimal` sem `@JsonFormat(shape=STRING)`,
 * mesma lacuna documentada em `moneyJson.ts` para `ExecucaoController`). Em `despesas`, o
 * `payload.valor` já chega como STRING (o produtor grava `"valor":"1200.00"` no JSON
 * armazenado — ver `PublicadorTransparenciaExecucao`), então o mesmo padrão de regex é
 * inofensivo ali: só substitui número cru, nunca casa uma string já entre aspas. */
const CAMPOS_MONETARIOS = ['valor', 'totalGeral'] as const;

function baseUrl(enteId: string): string {
  return `${apiOrigin}/transparencia/${enteId}`;
}

function queryString(filtro: Record<string, string | number | undefined>): string {
  const params = new URLSearchParams();
  for (const [chave, valor] of Object.entries(filtro)) {
    if (valor === undefined || valor === '') continue;
    params.set(chave, String(valor));
  }
  const texto = params.toString();
  return texto ? `?${texto}` : '';
}

async function get<TResponse>(path: string, filtro: Record<string, string | number | undefined>, options?: RequestOptions): Promise<TResponse> {
  const response = await fetch(`${path}${queryString(filtro)}`, {
    method: 'GET',
    headers: { Accept: 'application/json' },
    // Sem sessão nesta superfície (ver cabeçalho do arquivo) — `credentials: 'omit'` explícito
    // evita que um cookie de sessão do back-office (mesma origem) viaje sem necessidade numa
    // chamada pública, em vez de depender do padrão implícito do browser (`same-origin`).
    credentials: 'omit',
    signal: options?.signal,
  });
  const rawText = await response.text();
  const parsed = rawText ? parseJsonPreservingMoney(rawText, CAMPOS_MONETARIOS) : null;
  if (!response.ok) {
    throw new ApiError(response.status, 'erro_transparencia_publica', 'Falha ao consultar a API pública de transparência.');
  }
  return parsed as TResponse;
}

export const transparenciaClient = {
  despesas: (enteId: string, filtro: FiltroDespesasPublicas, options?: RequestOptions) =>
    get<DespesasPublicasResponse>(`${baseUrl(enteId)}/despesas`, filtro, options),

  totalizacoes: (enteId: string, filtro: FiltroDespesasPublicas, options?: RequestOptions) =>
    get<TotalizacaoTransparenciaResponse>(`${baseUrl(enteId)}/despesas/totalizacoes`, filtro, options),

  bulk: (enteId: string, filtro: FiltroDespesasPublicas, options?: RequestOptions) =>
    get<BulkManifestResponse>(`${baseUrl(enteId)}/despesas/bulk`, filtro, options),

  dicionarioDados: async (enteId: string, options?: RequestOptions): Promise<string> => {
    const response = await fetch(`${baseUrl(enteId)}/dicionario-dados`, {
      method: 'GET',
      headers: { Accept: 'text/markdown' },
      credentials: 'omit',
      signal: options?.signal,
    });
    const texto = await response.text();
    if (!response.ok) {
      throw new ApiError(response.status, 'erro_transparencia_publica', 'Falha ao consultar o dicionário de dados.');
    }
    return texto;
  },

  /** Navegação de página inteira (`<a href>`), não `fetch`: é um download de arquivo
   * (`text/csv`), mesmo padrão de `apiOrigin` já usado para o link de OAuth do gov.br
   * em `LoginPage.tsx`. Inclui a paginação/cursor corrente — "Exportar CSV desta consulta"
   * é deliberadamente a MESMA página que o cidadão está vendo (ADR-0060 D4), não a base
   * inteira (essa é `arquivoBulkUrl`, a partir do manifesto de `bulk`). */
  exportarCsvUrl: (enteId: string, filtro: FiltroDespesasPublicas): string =>
    `${baseUrl(enteId)}/despesas${queryString({ ...filtro, formato: 'csv' })}`,

  /** `arquivoCdn` do manifesto já é um path absoluto (`/cdn/transparencia/{enteId}/despesas/
   * {versao}.csv`) — resolve contra `apiOrigin` pelo mesmo motivo que `baseUrl` faz. */
  arquivoBulkUrl: (arquivoCdn: string): string => `${apiOrigin}${arquivoCdn}`,
};
