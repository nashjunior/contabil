/** Mesmo padrão de formatação de data já usado em `features/execucao/components/ExecucaoList.tsx`. */
export function formatarDataIso(dataIso: string): string {
  return new Date(`${dataIso}T00:00:00`).toLocaleDateString('pt-BR');
}

export function formatarInstante(instanteIso: string): string {
  return new Date(instanteIso).toLocaleDateString('pt-BR');
}

export function formatarInstanteComHora(instanteIso: string): string {
  return new Date(instanteIso).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

/** ID opaco (`credorId`/`orgaoId`/`contratoId`) — sem endpoint de nome (gap 3 do ADR-0060),
 * truncado visualmente com o valor completo em `title` para não estourar a coluna da tabela. */
export function truncarId(id: string, tamanho = 10): string {
  return id.length > tamanho ? `${id.slice(0, tamanho)}…` : id;
}
