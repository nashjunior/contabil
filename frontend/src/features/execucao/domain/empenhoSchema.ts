/**
 * Schema de validação do formulário de Empenho — deriva as regras do domínio
 * (`empenho.ts`), não as reimplementa (RAZ-202, princípio 3: proibido regra de valor em
 * dois lugares). Único import externo é `zod`; RHF consome isto via `zodResolver`, mas
 * a verdade de cada regra continua em `empenho.ts`/`shared/lib/dinheiro.ts` — este
 * arquivo só empacota os mesmos predicados num formato que o resolver entende.
 *
 * Campos permanecem string (shape bruto de `<input>`/`<select>`); a conversão para os
 * tipos finais do `EmpenhoRequest` (number, Money) acontece em
 * `application/registrarEmpenho.ts#paraEmpenhoRequest`, não aqui.
 */
import { z } from 'zod';
import { isValidMoney } from '../../../shared/lib/dinheiro';
import {
  ContratoId,
  CredorId,
  DotacaoId,
  TIPOS_EMPENHO,
  UnidadeGestoraId,
  isDataFatoValida,
  isExercicioValido,
  isTextoObrigatorioValido,
} from './empenho';

const MSG_UUID_INVALIDO = 'Informe um UUID válido.';

// Anotar `: boolean` no retorno é necessário: `XxxId.isValid` é um type predicate (`v is
// UuidVO<...>`) e, sem a anotação, o zod infere o refine como narrowing e vaza o VO
// (branded type) para `CamposEmpenho` — que deve continuar puro string (shape de <input>).
export const empenhoSchema = z.object({
  dotacaoId: z.string().refine((v): boolean => DotacaoId.isValid(v), MSG_UUID_INVALIDO),
  tipo: z.enum(TIPOS_EMPENHO, { message: 'Selecione o tipo.' }),
  credorId: z.string().refine((v): boolean => CredorId.isValid(v), MSG_UUID_INVALIDO),
  unidadeGestoraId: z.string().refine((v): boolean => UnidadeGestoraId.isValid(v), MSG_UUID_INVALIDO),
  contratoId: z
    .string()
    .refine((v): boolean => v === '' || ContratoId.isValid(v), 'Informe um UUID válido (ou deixe em branco).'),
  valor: z.string().refine((v) => isValidMoney(v.replace(',', '.')), 'Informe um valor decimal válido (ex.: 1000.00).'),
  dataFato: z.string().refine(isDataFatoValida, 'Informe a data do fato.'),
  exercicio: z.string().refine(isExercicioValido, 'Informe o exercício (aaaa).'),
  classificacaoOrcamentaria: z.string().refine(isTextoObrigatorioValido, 'Informe a classificação orçamentária.'),
  fonteRecurso: z.string().refine(isTextoObrigatorioValido, 'Informe a fonte de recurso.'),
  historico: z.string().refine(isTextoObrigatorioValido, 'Informe o histórico.'),
});

export type CamposEmpenho = z.infer<typeof empenhoSchema>;

export const CAMPOS_EMPENHO_VAZIOS: CamposEmpenho = {
  dotacaoId: '',
  // '' é o placeholder "Selecione…" do <select> nativo (FormSection.Select) — inválido até
  // o usuário escolher; o cast existe só para o tipo inicial, o resolver barra o submit.
  tipo: '' as CamposEmpenho['tipo'],
  credorId: '',
  unidadeGestoraId: '',
  contratoId: '',
  valor: '',
  dataFato: '',
  exercicio: String(new Date().getFullYear()),
  classificacaoOrcamentaria: '',
  fonteRecurso: '',
  historico: '',
};
