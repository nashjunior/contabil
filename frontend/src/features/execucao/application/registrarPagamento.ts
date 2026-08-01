import {
  execucaoClient,
  type GovbrContexto,
  type PagamentoRequest,
  type PagamentoResponse,
  type RequestOptions,
} from '../../../shared/api/client';
import { toMoney } from '../../../shared/lib/dinheiro';
import { isTextoObrigatorioValido } from '../domain/pagamento';
import type { CamposPagamento } from '../domain/pagamentoSchema';

/**
 * Caso de uso puro (ADR-0041): sem React nem React Query, nomeado para espelhar o use case real
 * do backend (`execucao-application/RegistrarPagamento.java`). `useRegistrarPagamento` é o
 * único adaptador de framework por cima disto.
 */
export function registrarPagamento(
  body: PagamentoRequest,
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<PagamentoResponse> {
  return execucaoClient.registrarPagamento(body, contexto, options);
}

/**
 * Mapeia os campos do formulário (já validados por `pagamentoSchema` — RAZ-202, princípio 4)
 * para o input deste caso de uso. `beneficiario` só vai omitido quando nenhum dos dois campos
 * foi preenchido (caso `folha_consolidada`, onde o schema não exige); preenchido parcialmente
 * por engano nunca chega aqui porque o schema já teria barrado o submit.
 *
 * Omitido (`undefined`), não `null`: `PagamentoRequest.beneficiario` no schema gerado
 * (`shared/api/generated/schema.ts`) é `Beneficiario | undefined`, não `| null` — o
 * `nullable: true` do contrato (`openapi/contrato-provisorio.yaml`) é irmão de um `$ref` puro,
 * que o `openapi-typescript`/spec OpenAPI 3.0 ignora (mesma pegadinha que `saldoDisponivel` já
 * contornou com `allOf`); o backend trata ausente e `null` do mesmo jeito
 * (`Optional.ofNullable(requisicao.beneficiario())`, `PagamentoController.java`), então omitir
 * é correto na prática — corrigir o contrato para refletir `| null` de verdade fica para quem
 * tocar `PagamentoRequest`/`PagamentoResponse` nesse arquivo (risco de colisão documentado,
 * fora do escopo desta issue).
 */
export function paraPagamentoRequest(campos: CamposPagamento): PagamentoRequest {
  const beneficiarioPreenchido =
    isTextoObrigatorioValido(campos.beneficiarioNome) || isTextoObrigatorioValido(campos.beneficiarioCpfCnpj);

  return {
    liquidacaoId: campos.liquidacaoId,
    dataCompetencia: campos.dataCompetencia,
    valor: toMoney(campos.valor),
    natureza: campos.natureza,
    beneficiario: beneficiarioPreenchido
      ? { nome: campos.beneficiarioNome.trim(), cpfCnpj: campos.beneficiarioCpfCnpj.trim() }
      : undefined,
    ordemBancaria: campos.ordemBancaria.trim() === '' ? null : campos.ordemBancaria.trim(),
    historico: campos.historico.trim(),
  };
}
