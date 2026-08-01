/**
 * Schema de validação do formulário de Liquidação — deriva do domínio (`liquidacao.ts` +
 * `shared/domain/data.ts` + `shared/lib/dinheiro.ts`), não o reimplementa (RAZ-202 princípio 3),
 * mesmo padrão de `empenhoSchema.ts`.
 *
 * `documentosSuporte` é array (Lei 4.320/1964 art. 63 §2º exige ao menos 1 documento,
 * `Liquidacao.validarDocumentosSuporte`) — a UI usa `useFieldArray` do RHF
 * (`LiquidacaoForm.tsx`), então o schema aceita o mesmo shape de array que o field array
 * produz (strings brutas; conversão para `DocumentoSuporte` real fica em
 * `application/registrarLiquidacao.ts`).
 */
import { z } from 'zod';
import { isDataIsoValida } from '../../../shared/domain/data';
import { isValidMoney } from '../../../shared/lib/dinheiro';
import { EmpenhoId, isTextoObrigatorioValido } from './liquidacao';

const MSG_UUID_INVALIDO = 'Informe um UUID válido.';

const documentoSuporteSchema = z.object({
  tipo: z.string().refine(isTextoObrigatorioValido, 'Informe o tipo do documento.'),
  numero: z.string().refine(isTextoObrigatorioValido, 'Informe o número do documento.'),
  dataEmissao: z.string().refine(isDataIsoValida, 'Informe a data de emissão.'),
  referenciaExterna: z.string(),
});

export const liquidacaoSchema = z.object({
  // empenhoId vem do `EmpenhoPicker` — nunca digitado — então vazio é "não escolheu ainda"
  // (mensagem própria), distinto de UUID malformado. Mesmo padrão de dotacaoId em
  // empenhoSchema.ts.
  empenhoId: z.string().superRefine((v, ctx) => {
    if (v.trim() === '') {
      ctx.addIssue({ code: 'custom', message: 'Selecione um empenho.' });
      return;
    }
    if (!EmpenhoId.isValid(v)) {
      ctx.addIssue({ code: 'custom', message: MSG_UUID_INVALIDO });
    }
  }),
  dataCompetencia: z.string().refine(isDataIsoValida, 'Informe a data de competência.'),
  valor: z.string().refine((v) => isValidMoney(v.replace(',', '.')), 'Informe um valor decimal válido (ex.: 1000.00).'),
  historico: z.string().refine(isTextoObrigatorioValido, 'Informe o histórico.'),
  documentosSuporte: z.array(documentoSuporteSchema).min(1, 'Inclua ao menos um documento de suporte.'),
});

export type CamposLiquidacao = z.infer<typeof liquidacaoSchema>;

export const DOCUMENTO_SUPORTE_VAZIO: CamposLiquidacao['documentosSuporte'][number] = {
  tipo: '',
  numero: '',
  dataEmissao: '',
  referenciaExterna: '',
};

export const CAMPOS_LIQUIDACAO_VAZIOS: CamposLiquidacao = {
  empenhoId: '',
  dataCompetencia: '',
  valor: '',
  historico: '',
  documentosSuporte: [DOCUMENTO_SUPORTE_VAZIO],
};
