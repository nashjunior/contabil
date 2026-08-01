/**
 * Wrapper RHF-compatível de `FormSection` (RAZ-202/ADR-0043) — sem tocar no pacote
 * `@siafic/design-system`. `FormSection`/`FormSection.Field`/`.Select`/`.Error` (RAZ-100/
 * ADR-0032/0033) já são compound components controlados por um único Context
 * `{values, errors, onChange}`; este hook só traduz esse mesmo contrato a partir de um
 * `UseFormReturn` do RHF, então `<FormSection {...useFormSectionRHF(form)}>` recebe os
 * subcomponentes existentes sem exigir nenhuma mudança no design system.
 *
 * Custo aceito: `form.watch()` sem argumento assina o formulário inteiro (todo campo
 * re-renderiza a seção a cada tecla), abrindo mão do ganho de performance de inputs
 * não controlados do RHF — ver ADR-0043 "Consequências" para o porquê e o follow-up
 * (primitivos do DS aceitando `value`/`onChange` direto, para casar com `Controller`
 * por campo em vez de um `watch()` único).
 */
import type { FieldErrors, FieldValues, Path, UseFormReturn } from 'react-hook-form';

export type PropsFormSectionRHF = {
  values: Record<string, string>;
  errors: Record<string, string | undefined>;
  onChange: (name: string, value: string) => void;
};

function paraMensagensDeErro(errors: FieldErrors): Record<string, string | undefined> {
  return Object.fromEntries(Object.entries(errors).map(([nome, erro]) => [nome, erro?.message as string | undefined]));
}

export function useFormSectionRHF<TCampos extends FieldValues>(form: UseFormReturn<TCampos>): PropsFormSectionRHF {
  const values = form.watch();

  function onChange(name: string, value: string) {
    form.setValue(name as Path<TCampos>, value as never, {
      shouldDirty: true,
      shouldValidate: form.formState.isSubmitted,
    });
  }

  return {
    values: values as unknown as Record<string, string>,
    errors: paraMensagensDeErro(form.formState.errors),
    onChange,
  };
}
