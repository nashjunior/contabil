/**
 * Espelha o componente "Alerta / Mensagem Inline (Alert)" do Figma
 * (design-system-tokens-componentes.md §2, linha 11; fileKey ObQu8oMQ0cEGbONMXgpuLU, node 49:6):
 * borda + fundo tonal por nível, Título (Label/Default, versalete) + Body (Body/Small),
 * sem ícone — nenhuma variante depende só de cor, o rótulo + o corpo carregam o significado.
 * Composição via children (não Context): Title/Body são filhos diretos de um único nível,
 * sem estado mutável compartilhado entre eles — não há travessia de 3+ níveis que justifique
 * Context aqui (ADR-0033); a cor por nível chega aos filhos por herança de `color` no container.
 * A11y: `danger`/`warning` → `role="alert"` (região assertiva); `info`/`success` → `role="status"`
 * (região polite) — mapeia 1:1 com `Nível=Crítico|Atenção` vs `Nível=Informativo|Sucesso` do Figma.
 */
import type { CSSProperties, ReactNode } from 'react';

export type AlertLevel = 'danger' | 'warning' | 'info' | 'success';

const ASSERTIVE_LEVELS: ReadonlySet<AlertLevel> = new Set(['danger', 'warning']);

function containerStyle(level: AlertLevel): CSSProperties {
  return {
    boxSizing: 'border-box',
    width: '100%',
    display: 'flex',
    flexDirection: 'column',
    gap: 'var(--spacing-xs)',
    padding: 'var(--spacing-sm) var(--spacing-md)',
    border: `1px solid var(--color-state-${level}-border)`,
    borderRadius: 'var(--radius-md)',
    background: `var(--color-state-${level}-bg)`,
    color: `var(--color-state-${level}-fg)`,
  };
}

const titleStyle: CSSProperties = {
  margin: 0,
  fontSize: '0.75rem',
  fontWeight: 'var(--typography-weight-medium)' as unknown as number,
  textTransform: 'uppercase',
  letterSpacing: '0.4px',
};

const bodyStyle: CSSProperties = {
  margin: 0,
  fontSize: '0.75rem',
  fontWeight: 'var(--typography-weight-regular)' as unknown as number,
  lineHeight: 1.333,
};

export type AlertProps = {
  level: AlertLevel;
  children: ReactNode;
};

export function Alert({ level, children }: AlertProps) {
  const role = ASSERTIVE_LEVELS.has(level) ? 'alert' : 'status';
  return (
    <div role={role} style={containerStyle(level)}>
      {children}
    </div>
  );
}

Alert.Title = function Title({ children }: { children: ReactNode }) {
  return <p style={titleStyle}>{children}</p>;
};

Alert.Body = function Body({ children }: { children: ReactNode }) {
  return <p style={bodyStyle}>{children}</p>;
};
