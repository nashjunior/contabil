import { NavLink } from 'react-router-dom';

const LINKS = [
  { to: '/execucao', label: 'Execução orçamentária' },
  { to: '/execucao/liquidacoes', label: 'Registrar liquidação' },
  { to: '/execucao/pagamentos', label: 'Registrar pagamento' },
  { to: '/execucao/aprovacoes', label: 'Fila de aprovação' },
  { to: '/razao/saldo', label: 'Saldo por conta' },
  { to: '/razao/balancete', label: 'Balancete' },
  { to: '/razao/contas', label: 'Catálogo de contas' },
] as const;

/** Navegação mínima entre as telas autenticadas (RAZ-191) — sem layout/shell compartilhado
 * ainda (fora do escopo desta issue); só os links necessários para as telas de consulta do
 * razão deixarem de ser inalcançáveis a partir da tela de execução. */
export function FeatureNav() {
  return (
    <nav aria-label="Navegação principal" style={{ display: 'flex', gap: 'var(--spacing-lg)', marginBottom: 'var(--spacing-lg)' }}>
      {LINKS.map((link) => (
        <NavLink
          key={link.to}
          to={link.to}
          style={({ isActive }) => ({
            color: isActive ? 'var(--color-brand-default)' : 'var(--color-text-secondary)',
            fontWeight: isActive ? 'var(--typography-weight-bold)' : 'var(--typography-weight-regular)',
            textDecoration: 'none',
          })}
        >
          {link.label}
        </NavLink>
      ))}
    </nav>
  );
}
