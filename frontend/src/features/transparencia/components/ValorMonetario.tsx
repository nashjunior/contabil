/**
 * Espelha `Valor Monetário` do Figma (node 6:12, `Ênfase=Padrão|Forte`) — alinhado à direita,
 * `Ênfase=Forte` para totais/destaques (Tela 1b). Dinheiro decimal (invariante do backend):
 * `valor` chega como `Money` (string) de `formatMoneyBRL`, nunca convertido para `number` aqui.
 */
import { formatMoneyBRL } from '../../../shared/lib/dinheiro';

export function ValorMonetario({ valor, enfase = 'padrao' }: { valor: string; enfase?: 'padrao' | 'forte' }) {
  return (
    <span
      style={{
        display: 'block',
        textAlign: 'right',
        whiteSpace: 'nowrap',
        fontVariantNumeric: 'tabular-nums',
        fontSize: enfase === 'forte' ? 'var(--typography-size-lg)' : 'var(--typography-size-base)',
        fontWeight: enfase === 'forte' ? 'var(--typography-weight-bold)' : 'var(--typography-weight-regular)',
        color: 'var(--color-text-primary)',
      }}
    >
      {formatMoneyBRL(valor)}
    </span>
  );
}
