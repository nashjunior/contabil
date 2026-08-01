/**
 * Hierarquia derivada, não servida (ADR-0030 §6/RAZ-142): `GET /razao/contas` devolve uma
 * lista PLANA ordenada por `codigo` (sem endpoint "filhos de X"). A indentação visual entre
 * conta sintética (ex.: "1.1 Ativo circulante") e analítica (ex.: "1.1.1 Caixa...") é
 * calculada aqui, client-side, pelo nº de segmentos do código PCASP — mesmo cálculo usado
 * pelo Picker de Conta PCASP (Select.Option `depth`) e pelo Catálogo de Contas.
 */
export function depthFromCodigo(codigo: string): number {
  return Math.max(0, codigo.split('.').length - 1);
}
