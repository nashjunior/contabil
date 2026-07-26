/**
 * Compound component (ADR-0032): Field/Error leem o estado via Context/hook, nao
 * via prop repassada pelo pai. Uso:
 *   <FormSection legend="Empenho" values={values} errors={errors} onChange={set}>
 *     <FormSection.Field name="numero" label="Número" />
 *     <FormSection.Error name="numero" />
 *   </FormSection>
 * Co-location: Context + subcomponentes no mesmo arquivo — o consumidor externo
 * so importa `FormSection` (index.ts), nunca o Context bruto.
 */
import { createContext, useContext, useId, type ChangeEvent, type ReactNode } from 'react';

type FormSectionState = {
  sectionId: string;
  values: Record<string, string>;
  errors: Record<string, string | undefined>;
  onChange: (name: string, value: string) => void;
};

const FormSectionContext = createContext<FormSectionState | null>(null);

function useFormSectionContext(): FormSectionState {
  const ctx = useContext(FormSectionContext);
  if (!ctx) throw new Error('FormSection.Field/Error deve ser usado dentro de <FormSection>');
  return ctx;
}

type FormSectionProps = Omit<FormSectionState, 'sectionId'> & {
  legend: string;
  children: ReactNode;
};

export function FormSection({ legend, children, ...state }: FormSectionProps) {
  const sectionId = useId();
  return (
    <FormSectionContext.Provider value={{ sectionId, ...state }}>
      <fieldset style={{ border: '1px solid var(--color-border-default)', borderRadius: 'var(--radius-md)', padding: 'var(--spacing-lg)', marginBottom: 'var(--spacing-lg)' }}>
        <legend>{legend}</legend>
        {children}
      </fieldset>
    </FormSectionContext.Provider>
  );
}

type FieldProps = {
  name: string;
  label: string;
  type?: string;
  required?: boolean;
  inputMode?: 'text' | 'numeric' | 'decimal';
};

FormSection.Field = function Field({ name, label, type = 'text', required, inputMode }: FieldProps) {
  const { sectionId, values, errors, onChange } = useFormSectionContext();
  const inputId = `${sectionId}-${name}`;
  const errorId = `${sectionId}-${name}-erro`;
  const hasError = Boolean(errors[name]);

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    onChange(name, event.target.value);
  }

  return (
    <div style={{ marginBottom: 'var(--spacing-md)' }}>
      <label htmlFor={inputId}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      <input
        id={inputId}
        name={name}
        type={type}
        inputMode={inputMode}
        required={required}
        value={values[name] ?? ''}
        onChange={handleChange}
        aria-invalid={hasError || undefined}
        aria-describedby={hasError ? errorId : undefined}
      />
    </div>
  );
};

type SelectProps = {
  name: string;
  label: string;
  options: Array<{ value: string; label: string }>;
  required?: boolean;
};

FormSection.Select = function Select({ name, label, options, required }: SelectProps) {
  const { sectionId, values, errors, onChange } = useFormSectionContext();
  const inputId = `${sectionId}-${name}`;
  const errorId = `${sectionId}-${name}-erro`;
  const hasError = Boolean(errors[name]);

  return (
    <div style={{ marginBottom: 'var(--spacing-md)' }}>
      <label htmlFor={inputId}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      <select
        id={inputId}
        name={name}
        required={required}
        value={values[name] ?? ''}
        onChange={(event) => onChange(name, event.target.value)}
        aria-invalid={hasError || undefined}
        aria-describedby={hasError ? errorId : undefined}
      >
        <option value="" disabled>
          Selecione…
        </option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
};

FormSection.Error = function Error({ name }: { name: string }) {
  const { sectionId, errors } = useFormSectionContext();
  if (!errors[name]) return null;
  return (
    <span id={`${sectionId}-${name}-erro`} role="alert" style={{ display: 'block', color: 'var(--color-state-danger-fg)' }}>
      {errors[name]}
    </span>
  );
};
