/**
 * Schema de validação do formulário de Pagamento — deriva do domínio (`pagamento.ts` +
 * `shared/domain/data.ts` + `shared/lib/dinheiro.ts`). Beneficiário nominal é
 * condicionalmente obrigatório: toda natureza exceto `folha_consolidada` exige nome e CPF/CNPJ
 * (`Pagamento.validarEntrada`) — validado via `superRefine` no objeto inteiro (não por campo
 * isolado) porque a regra depende do valor de OUTRO campo (`natureza`).
 */
import { z } from 'zod';
import { isDataIsoValida } from '../../../shared/domain/data';
import { isValidMoney } from '../../../shared/lib/dinheiro';
import { LiquidacaoId, TIPOS_NATUREZA_PAGAMENTO, isTextoObrigatorioValido } from './pagamento';

const MSG_UUID_INVALIDO = 'Informe um UUID válido.';

export const pagamentoSchema = z
  .object({
    // liquidacaoId vem do `LiquidacaoAprovadaPicker` — nunca digitado — mesmo padrão de
    // empenhoId em liquidacaoSchema.ts.
    liquidacaoId: z.string().superRefine((v, ctx) => {
      if (v.trim() === '') {
        ctx.addIssue({ code: 'custom', message: 'Selecione uma liquidação aprovada.' });
        return;
      }
      if (!LiquidacaoId.isValid(v)) {
        ctx.addIssue({ code: 'custom', message: MSG_UUID_INVALIDO });
      }
    }),
    dataCompetencia: z.string().refine(isDataIsoValida, 'Informe a data de competência.'),
    valor: z.string().refine((v) => isValidMoney(v.replace(',', '.')), 'Informe um valor decimal válido (ex.: 1000.00).'),
    natureza: z.enum(TIPOS_NATUREZA_PAGAMENTO, { message: 'Selecione a natureza do pagamento.' }),
    beneficiarioNome: z.string(),
    beneficiarioCpfCnpj: z.string(),
    ordemBancaria: z.string(),
    historico: z.string().refine(isTextoObrigatorioValido, 'Informe o histórico.'),
  })
  .superRefine((campos, ctx) => {
    if (campos.natureza === 'folha_consolidada') return;
    if (!isTextoObrigatorioValido(campos.beneficiarioNome)) {
      ctx.addIssue({ code: 'custom', path: ['beneficiarioNome'], message: 'Informe o nome do beneficiário.' });
    }
    if (!isTextoObrigatorioValido(campos.beneficiarioCpfCnpj)) {
      ctx.addIssue({ code: 'custom', path: ['beneficiarioCpfCnpj'], message: 'Informe o CPF/CNPJ do beneficiário.' });
    }
  });

export type CamposPagamento = z.infer<typeof pagamentoSchema>;

export const CAMPOS_PAGAMENTO_VAZIOS: CamposPagamento = {
  liquidacaoId: '',
  // '' é o placeholder "Selecione…" do <select> nativo (FormSection.Select) — inválido até o
  // usuário escolher; o cast existe só para o tipo inicial, o resolver barra o submit.
  natureza: '' as CamposPagamento['natureza'],
  dataCompetencia: '',
  valor: '',
  beneficiarioNome: '',
  beneficiarioCpfCnpj: '',
  ordemBancaria: '',
  historico: '',
};
