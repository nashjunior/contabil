/**
 * Espelha `Badge Estágio` do Figma (node 13:11) — pílula com texto versalete, nunca só cor
 * (UX-1/a11y, o rótulo em si já carrega o significado). Reforça UX-1: o mesmo componente do
 * back-office reaparece aqui (mesma paleta `--color-state-{tone}-*`), mesmo sem contraparte
 * ainda publicada em `@siafic/design-system` (§3 do design-system-tokens-componentes.md).
 */
import { rotuloEstagio, toneEstagio } from '../domain/estagio';

export function BadgeEstagio({ estagio }: { estagio: string }) {
  const tone = toneEstagio(estagio);
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        padding: 'var(--spacing-2xs) var(--spacing-sm)',
        borderRadius: 'var(--radius-full)',
        background: `var(--color-state-${tone}-bg)`,
        color: `var(--color-state-${tone}-fg)`,
        border: `1px solid var(--color-state-${tone}-border)`,
        fontSize: 'var(--typography-size-sm)',
        fontWeight: 'var(--typography-weight-medium)',
        letterSpacing: '0.4px',
        textTransform: 'uppercase',
        whiteSpace: 'nowrap',
      }}
    >
      {rotuloEstagio(estagio)}
    </span>
  );
}
