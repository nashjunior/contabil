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
import { createContext, useContext, useId, useState, type ChangeEvent, type CSSProperties, type ReactNode } from 'react';

/**
 * Espelha o componente "Campo de Valor" do Figma (design-system-tokens-componentes.md §2, linha 2;
 * RAZ-194 — auditoria de fidelidade pós-Select): label versalete (Label/Default), radius-sm,
 * borda 2px em foco, borda danger em erro. Aplicado tambem a `FormSection.Select` (mesma casca
 * visual de campo, sem contraparte Figma própria além do Select customizado da RAZ-122/145).
 */
const fieldLabelStyle: CSSProperties = {
  display: 'block',
  marginBottom: 'var(--spacing-2xs)',
  fontSize: '0.75rem',
  fontWeight: 'var(--typography-weight-medium)' as unknown as number,
  color: 'var(--color-text-secondary)',
  textTransform: 'uppercase',
  letterSpacing: '0.4px',
};

function fieldControlStyle(options: { focused: boolean; hasError: boolean; disabled?: boolean }): CSSProperties {
  const { focused, hasError, disabled } = options;
  const borderColor = disabled
    ? 'var(--color-border-default)'
    : hasError
      ? 'var(--color-state-danger-border)'
      : focused
        ? 'var(--color-border-focus)'
        : 'var(--color-border-default)';
  return {
    boxSizing: 'border-box',
    width: '100%',
    border: `${focused && !hasError ? '2px' : '1px'} solid ${borderColor}`,
    borderRadius: 'var(--radius-sm)',
    padding: 'var(--spacing-sm) var(--spacing-md)',
    fontSize: 'var(--typography-size-sm)',
    fontFamily: 'var(--typography-family-base)',
    color: disabled ? 'var(--color-text-disabled)' : 'var(--color-text-primary)',
    background: disabled ? 'var(--color-bg-disabled)' : 'var(--color-bg-surface)',
    cursor: disabled ? 'not-allowed' : undefined,
  };
}

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
  const [focused, setFocused] = useState(false);

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    onChange(name, event.target.value);
  }

  return (
    <div style={{ marginBottom: 'var(--spacing-md)' }}>
      <label htmlFor={inputId} style={fieldLabelStyle}>
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
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        aria-invalid={hasError || undefined}
        aria-describedby={hasError ? errorId : undefined}
        style={fieldControlStyle({ focused, hasError })}
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
  const [focused, setFocused] = useState(false);

  return (
    <div style={{ marginBottom: 'var(--spacing-md)' }}>
      <label htmlFor={inputId} style={fieldLabelStyle}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      <select
        id={inputId}
        name={name}
        required={required}
        value={values[name] ?? ''}
        onChange={(event) => onChange(name, event.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        aria-invalid={hasError || undefined}
        aria-describedby={hasError ? errorId : undefined}
        style={fieldControlStyle({ focused, hasError })}
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
    <span
      id={`${sectionId}-${name}-erro`}
      role="alert"
      style={{
        display: 'block',
        marginTop: 'var(--spacing-2xs)',
        fontSize: '0.75rem',
        color: 'var(--color-state-danger-fg)',
      }}
    >
      {errors[name]}
    </span>
  );
};
