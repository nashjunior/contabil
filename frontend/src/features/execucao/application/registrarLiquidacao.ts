import {
  execucaoClient,
  type GovbrContexto,
  type LiquidacaoRequest,
  type LiquidacaoResponse,
  type RequestOptions,
} from '../../../shared/api/client';
import { toMoney } from '../../../shared/lib/dinheiro';
import type { CamposLiquidacao } from '../domain/liquidacaoSchema';

/**
 * Caso de uso puro (ADR-0041): sem React nem React Query, nomeado para espelhar o use case real
 * do backend (`execucao-application/RegistrarLiquidacao.java`). `useRegistrarLiquidacao` é o
 * único adaptador de framework por cima disto.
 */
export function registrarLiquidacao(
  body: LiquidacaoRequest,
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<LiquidacaoResponse> {
  return execucaoClient.registrarLiquidacao(body, contexto, options);
}

/**
 * Mapeia os campos do formulário (já validados por `liquidacaoSchema` — RAZ-202, princípio 4)
 * para o input deste caso de uso. Não revalida: o resolver do RHF já barrou o submit se algum
 * campo violasse o domínio.
 */
export function paraLiquidacaoRequest(campos: CamposLiquidacao): LiquidacaoRequest {
  return {
    empenhoId: campos.empenhoId,
    dataCompetencia: campos.dataCompetencia,
    valor: toMoney(campos.valor),
    historico: campos.historico.trim(),
    documentosSuporte: campos.documentosSuporte.map((documento) => ({
      tipo: documento.tipo.trim(),
      numero: documento.numero.trim(),
      dataEmissao: documento.dataEmissao,
      referenciaExterna: documento.referenciaExterna.trim() === '' ? null : documento.referenciaExterna.trim(),
    })),
  };
}
