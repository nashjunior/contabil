/**
 * Schema de validação do modal de decisão do gate 4-eyes (ADR-0055, decisão 3) — motivo só é
 * obrigatório quando `decisao === 'devolver'`, exatamente o código-fonte proposto na decisão
 * (reusa `isTextoObrigatorioValido`, mesmo VO de `historico`/documentos em `liquidacao.ts`).
 */
import { z } from 'zod';
import { isTextoObrigatorioValido } from './liquidacao';

export const decisaoAprovacaoSchema = z
  .object({
    decisao: z.enum(['aprovar', 'devolver']),
    motivo: z.string(),
  })
  .superRefine((v, ctx) => {
    if (v.decisao === 'devolver' && !isTextoObrigatorioValido(v.motivo)) {
      ctx.addIssue({ code: 'custom', path: ['motivo'], message: 'Informe o motivo da devolução.' });
    }
  });

export type CamposDecisaoAprovacao = z.infer<typeof decisaoAprovacaoSchema>;

export const CAMPOS_DECISAO_APROVACAO_VAZIOS: CamposDecisaoAprovacao = {
  decisao: 'aprovar',
  motivo: '',
};
