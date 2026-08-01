/**
 * Padrão reutilizável (RAZ-202/ADR-0043) para formulários que registram um agregado via
 * caso de uso `application/` (ex.: `registrarEmpenho`, e futuramente liquidação/pagamento/
 * dotação). Encapsula a plumbing comum de RHF + zodResolver + chamada do caso de uso, para
 * que cada formulário só declare schema, valores iniciais, o mapeamento campos→input e o
 * caso de uso em si — nunca reimplemente onSubmit/estado de envio na mão.
 *
 * Este hook é o DRIVING ADAPTER (borda de UI, princípio 1 da RAZ-202): conhece RHF/zod,
 * mas nenhuma regra de domínio mora aqui — `schema` já vem derivado do domínio pelo
 * chamador, e `executar` é sempre um caso de uso puro de `features/<feature>/application/`,
 * nunca o client de API direto.
 */
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm, type DefaultValues, type FieldValues } from 'react-hook-form';
import type { ZodType } from 'zod';

export type OpcoesFormDeAgregado<TCampos extends FieldValues, TInput, TResultado> = {
  // Input = Output (TCampos, TCampos): nenhum schema aqui usa `.transform()`, e o resolver
  // do RHF exige que o Input do zod (`_zod.input`) seja atribuível a `FieldValues`, o que o
  // default de `ZodType` (`Input = unknown`) não satisfaz.
  schema: ZodType<TCampos, TCampos>;
  valoresIniciais: DefaultValues<TCampos>;
  /** Mapeia os campos já validados pelo schema para o input do caso de uso (RAZ-202, princípio 4). */
  paraInput: (campos: TCampos) => TInput;
  /** Caso de uso puro (`features/<feature>/application/`) — nunca o client de API direto. */
  executar: (input: TInput) => Promise<TResultado>;
  aoRegistrar?: (resultado: TResultado) => void;
};

export function useFormDeAgregado<TCampos extends FieldValues, TInput, TResultado>(
  opcoes: OpcoesFormDeAgregado<TCampos, TInput, TResultado>,
) {
  const form = useForm<TCampos>({
    resolver: zodResolver(opcoes.schema),
    defaultValues: opcoes.valoresIniciais,
  });

  const aoSubmeter = form.handleSubmit(async (campos) => {
    try {
      const resultado = await opcoes.executar(opcoes.paraInput(campos));
      // keepIsSubmitSuccessful: a UI (ex.: "Empenho registrado.") lê isSubmitSuccessful;
      // sem isto, o próprio reset apagaria a flag antes do primeiro re-render pós-sucesso.
      form.reset(opcoes.valoresIniciais, { keepIsSubmitSuccessful: true });
      opcoes.aoRegistrar?.(resultado);
    } catch (erro) {
      form.setError('root.envio', {
        type: 'envio',
        message: erro instanceof Error ? erro.message : 'Não foi possível concluir a operação.',
      });
    }
  });

  return { form, aoSubmeter };
}
