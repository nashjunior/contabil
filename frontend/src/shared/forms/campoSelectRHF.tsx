/**
 * Wrapper RHF-compatível do `Select` rico (typeahead/assíncrono — RAZ-122/145) via
 * `Controller` (RAZ-202/ADR-0043). `EmpenhoForm` não usa isto ainda (seu campo `tipo` é
 * um enum fixo de 3 opções, resolvido pelo `<select>` nativo de `FormSection.Select`);
 * este wrapper existe para os pickers com busca que liquidação/pagamento/dotação vão
 * precisar (autocomplete de dotação/credor/unidade gestora — ver gap citado no
 * cabeçalho antigo de `EmpenhoForm.tsx`), para não reinventar a integração then.
 *
 * `Controller` (não `register`) porque `Select` já é controlado por `value`/`onChange`
 * (compound component, ADR-0032/0033) — a mesma forma que o `field` do `Controller`
 * expõe, sem precisar de nenhuma mudança no pacote `@siafic/design-system`.
 */
import { Select, type SelectOption } from '@siafic/design-system';
import { Controller, type Control, type FieldValues, type Path } from 'react-hook-form';

type CampoSelectRHFProps<TCampos extends FieldValues> = {
  name: Path<TCampos>;
  control: Control<TCampos>;
  options: SelectOption[];
  placeholder?: string;
  ariaLabel: string;
};

export function CampoSelectRHF<TCampos extends FieldValues>({
  name,
  control,
  options,
  placeholder,
  ariaLabel,
}: CampoSelectRHFProps<TCampos>) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <Select
          value={(field.value as string) || null}
          onChange={(value) => field.onChange(value ?? '')}
          options={options}
          placeholder={placeholder}
        >
          <Select.Trigger aria-label={ariaLabel} />
          <Select.Options />
        </Select>
      )}
    />
  );
}
