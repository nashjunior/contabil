import type { ReactNode } from 'react';

/**
 * Pill de status reusado pelo indicador "Confere" (`BalanceteTable`) e pela tag
 * Analítica/Sintética (`ContasCatalogoTable`) — o design system ainda não tem um
 * componente `Tag` embarcado (só `Select`/`FormSection`); os tokens `--color-state-*`
 * já são o mesmo padrão aplicado em `Select.stories.tsx`.
 */
export function StatusPill({ tone, children }: { tone: 'success' | 'danger' | 'neutral'; children: ReactNode }) {
  const background = tone === 'neutral' ? 'var(--color-bg-inset)' : `var(--color-state-${tone}-bg)`;
  const color = tone === 'neutral' ? 'var(--color-text-secondary)' : `var(--color-state-${tone}-fg)`;

  return (
    <span
      role={tone === 'neutral' ? undefined : 'status'}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 'var(--spacing-2xs)',
        padding: 'var(--spacing-2xs) var(--spacing-sm)',
        borderRadius: 'var(--radius-full)',
        background,
        color,
        fontSize: 'var(--typography-size-sm)',
        fontWeight: 'var(--typography-weight-medium)',
      }}
    >
      {children}
    </span>
  );
}
