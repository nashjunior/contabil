import { useState } from 'react';
import type { Meta, StoryObj } from '@storybook/react-vite';
import { FormSection } from './FormSection';

const meta: Meta<typeof FormSection> = {
  title: 'Componentes/FormSection',
  component: FormSection,
  tags: ['autodocs'],
};
export default meta;

type Story = StoryObj<typeof FormSection>;

const TIPOS_EMPENHO = [
  { value: 'ORDINARIO', label: 'Ordinário' },
  { value: 'ESTIMATIVO', label: 'Estimativo' },
  { value: 'GLOBAL', label: 'Global' },
];

function EmpenhoFormDemo() {
  const [values, setValues] = useState<Record<string, string>>({ numero: '', tipo: '' });

  function onChange(name: string, value: string) {
    setValues((current) => ({ ...current, [name]: value }));
  }

  return (
    <FormSection legend="Empenho" values={values} errors={{}} onChange={onChange}>
      <FormSection.Field name="numero" label="Número" required />
      <FormSection.Error name="numero" />
      <FormSection.Select name="tipo" label="Tipo" required options={TIPOS_EMPENHO} />
      <FormSection.Error name="tipo" />
    </FormSection>
  );
}

export const Padrao: Story = {
  render: () => <EmpenhoFormDemo />,
};

function ComErroDemo() {
  const [values, setValues] = useState<Record<string, string>>({ numero: '2026/000123' });

  return (
    <FormSection
      legend="Empenho"
      values={values}
      errors={{ numero: 'Número já utilizado neste exercício.' }}
      onChange={(name, value) => setValues((current) => ({ ...current, [name]: value }))}
    >
      <FormSection.Field name="numero" label="Número" required />
      <FormSection.Error name="numero" />
    </FormSection>
  );
}

export const ComErro: Story = {
  render: () => <ComErroDemo />,
};
