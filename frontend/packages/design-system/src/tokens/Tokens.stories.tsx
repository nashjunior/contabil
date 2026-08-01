import type { Meta, StoryObj } from '@storybook/react-vite';
import { theme } from './theme';

const meta: Meta = {
  title: 'Foundations/Tokens',
  parameters: {
    // Página de referência (swatches), não um componente interativo — sem gate de a11y.
    a11y: { test: 'off' },
  },
};
export default meta;

type Story = StoryObj;

function Swatch({ name, value }: { name: string; value: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-md)', marginBottom: 'var(--spacing-sm)' }}>
      <div
        style={{
          width: 48,
          height: 48,
          flexShrink: 0,
          borderRadius: 'var(--radius-sm)',
          border: '1px solid var(--color-border-default)',
          background: value,
        }}
      />
      <div>
        <div style={{ fontFamily: 'var(--typography-family-base)', fontSize: 'var(--typography-size-sm)' }}>{name}</div>
        <code style={{ fontSize: 'var(--typography-size-sm)', color: 'var(--color-text-secondary)' }}>{value}</code>
      </div>
    </div>
  );
}

function ColorSection({ title, tokens }: { title: string; tokens: Record<string, string> }) {
  return (
    <section style={{ marginBottom: 'var(--spacing-xl)' }}>
      <h3>{title}</h3>
      {Object.entries(tokens).map(([name, value]) => (
        <Swatch key={name} name={name} value={value} />
      ))}
    </section>
  );
}

export const Cores: Story = {
  render: () => (
    <div>
      <ColorSection title="Superfícies (color.bg.*)" tokens={theme.color.bg} />
      <ColorSection title="Texto (color.text.*)" tokens={theme.color.text} />
      <ColorSection title="Bordas (color.border.*)" tokens={theme.color.border} />
      <ColorSection
        title="Marca (color.brand.*)"
        tokens={{
          default: theme.color.brand.default,
          hover: theme.color.brand.hover,
          pressed: theme.color.brand.pressed,
          subtleBg: theme.color.brand.subtleBg,
        }}
      />
      <ColorSection title="Estado — sucesso (color.state.success.*)" tokens={theme.color.state.success} />
      <ColorSection title="Estado — alerta (color.state.warning.*)" tokens={theme.color.state.warning} />
      <ColorSection title="Estado — perigo (color.state.danger.*)" tokens={theme.color.state.danger} />
      <ColorSection title="Estado — informação (color.state.info.*)" tokens={theme.color.state.info} />
      <ColorSection title="PII mascarada (color.pii.*)" tokens={theme.color.pii} />
    </div>
  ),
};

export const Espacamento: Story = {
  render: () => (
    <div>
      {Object.entries(theme.spacing).map(([name, value]) => (
        <div key={name} style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-md)', marginBottom: 'var(--spacing-xs)' }}>
          <div style={{ width: value, height: 16, background: 'var(--color-brand-default)' }} />
          <code>
            spacing.{name} = {value}
          </code>
        </div>
      ))}
    </div>
  ),
};

export const Raio: Story = {
  render: () => (
    <div style={{ display: 'flex', gap: 'var(--spacing-lg)', flexWrap: 'wrap' }}>
      {Object.entries(theme.radius).map(([name, value]) => (
        <div key={name} style={{ textAlign: 'center' }}>
          <div
            style={{
              width: 64,
              height: 64,
              borderRadius: value,
              background: 'var(--color-brand-subtle-bg)',
              border: '1px solid var(--color-border-default)',
            }}
          />
          <code>radius.{name}</code>
        </div>
      ))}
    </div>
  ),
};

export const Tipografia: Story = {
  render: () => (
    <div>
      {Object.entries(theme.typography.size).map(([name, value]) => (
        <p key={name} style={{ fontSize: value, fontFamily: theme.typography.family.base.join(', ') }}>
          typography.size.{name} ({value}) — Demonstração de Empenho nº 2026/000123
        </p>
      ))}
    </div>
  ),
};
