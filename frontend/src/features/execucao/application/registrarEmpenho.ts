import { execucaoClient, type EmpenhoRequest, type EmpenhoResponse, type GovbrContexto, type RequestOptions } from '../../../shared/api/client';
import { toMoney } from '../../../shared/lib/dinheiro';
import type { CamposEmpenho } from '../domain/empenhoSchema';

/**
 * Caso de uso puro (ADR-0041): sem React nem React Query, nomeado para
 * espelhar o use case real do backend (`execucao-application/RegistrarEmpenho.java`).
 * `useCriarEmpenho` é o único adaptador de framework por cima disto — cache/
 * invalidação do React Query ficam no hook, não aqui.
 */
export function registrarEmpenho(
  body: EmpenhoRequest,
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<EmpenhoResponse> {
  return execucaoClient.registrarEmpenho(body, contexto, options);
}

/**
 * Mapeia os campos do formulário (já validados pelo `empenhoSchema` — RAZ-202, princípio 4)
 * para o input deste caso de uso. Não revalida: o resolver do RHF já barrou o submit se
 * algum campo violasse o domínio, então isto é mapeamento estrutural puro, não uma segunda
 * cópia da regra de valor.
 */
export function paraEmpenhoRequest(campos: CamposEmpenho): EmpenhoRequest {
  return {
    dotacaoId: campos.dotacaoId,
    tipo: campos.tipo,
    credorId: campos.credorId,
    unidadeGestoraId: campos.unidadeGestoraId,
    contratoId: campos.contratoId === '' ? null : campos.contratoId,
    valor: toMoney(campos.valor),
    dataFato: campos.dataFato,
    exercicio: Number(campos.exercicio),
    classificacaoOrcamentaria: campos.classificacaoOrcamentaria.trim(),
    fonteRecurso: campos.fonteRecurso.trim(),
    historico: campos.historico.trim(),
  };
}
