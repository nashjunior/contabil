/**
 * Domínio do agregado Liquidação no front — framework-free (zero import de React/RHF/zod),
 * espelhando os value objects e invariantes de `execucao-domain` (`Liquidacao.java`,
 * `DocumentoSuporte.java`) — RAZ-230, mesmo padrão de `domain/empenho.ts` (RAZ-202/ADR-0043).
 * `EmpenhoId`/`LiquidacaoId` completam os VOs que `empenho.ts` não precisava (o empenho não
 * referencia a si mesmo, e liquidação não tinha tela até esta issue).
 */
import { criarTipoIdVO } from '../../../shared/domain/uuidVO';

export const EmpenhoId = criarTipoIdVO<'EmpenhoId'>('ID do empenho');
export const LiquidacaoId = criarTipoIdVO<'LiquidacaoId'>('ID da liquidação');

/** Texto obrigatório (histórico, tipo/número de documento de suporte). */
export function isTextoObrigatorioValido(valor: string): boolean {
  return valor.trim().length > 0;
}
