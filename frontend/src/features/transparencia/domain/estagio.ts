/**
 * Espelha `Badge Estágio` do Figma (design-system-tokens-componentes.md §2 linha 5, fileKey
 * ObQu8oMQ0cEGbONMXgpuLU, node 13:11) e reforça UX-1 (ADR-0059): o mesmo componente/paleta do
 * back-office, aqui só com código (o pacote `@siafic/design-system` ainda não exporta um Badge
 * Estágio — ver design-system-tokens-componentes.md §3, só `Alert`/`Select`/`FormSection`
 * existem hoje). Cores lidas ao vivo do Figma via `get_variable_defs` (não inventadas):
 * Empenhado=neutral, Liquidado=info, Pago=success.
 *
 * Só os 3 estágios que a barra de filtros do wireframe lista (Todos/Empenhado/Liquidado/Pago)
 * — `receita`/`desconhecido` existem no enum do backend (`TransparenciaPayloadPublico.
 * estagioDe`/constraint do banco) mas nenhum produtor emite `receita` hoje (ADR-0059: "quando
 * o produtor de receita existir") e `desconhecido` é só fallback defensivo — não viram opção
 * de filtro nem chip, evita a UI prometer um dado que a API nunca envia na prática.
 */
export const ESTAGIOS_FILTRAVEIS = ['empenhado', 'liquidado', 'pago'] as const;

export type EstagioFiltravel = (typeof ESTAGIOS_FILTRAVEIS)[number];

const ROTULOS: Record<string, string> = {
  empenhado: 'Empenhado',
  liquidado: 'Liquidado',
  pago: 'Pago',
  receita: 'Receita',
  desconhecido: 'Desconhecido',
};

export function rotuloEstagio(estagio: string): string {
  return ROTULOS[estagio] ?? estagio;
}

export type ToneEstagio = 'neutral' | 'info' | 'success';

const TONS: Record<string, ToneEstagio> = {
  empenhado: 'neutral',
  liquidado: 'info',
  pago: 'success',
};

export function toneEstagio(estagio: string): ToneEstagio {
  return TONS[estagio] ?? 'neutral';
}

/** `CamposOrdenacaoTransparenciaPublica` (verificado no código) — a API rejeita qualquer
 * campo fora desta lista; o seletor "Ordenar por" não pode oferecer mais que isso (ADR-0060
 * decisão 6). Padrão documentado pela própria API: `publicadoEm` desc, "mais recente primeiro
 * por data de publicação". */
export const CAMPOS_ORDENACAO = [
  { valor: 'publicadoEm', rotulo: 'Publicado em (padrão)' },
  { valor: 'data', rotulo: 'Data do fato/competência' },
  { valor: 'valor', rotulo: 'Valor' },
  { valor: 'numeroEmpenho', rotulo: 'Nº do empenho' },
] as const;
