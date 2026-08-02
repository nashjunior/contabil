import { Link, useLocation } from 'react-router-dom';

type NavItem = { to: string; label: string };
type NavArea = { id: string; label: string; items: readonly NavItem[] };

/**
 * IA da RAZ-233 (ADR-0026 §RAZ-233, design-system-tokens-componentes.md §5.1): o Figma agrupa
 * a navegação em 6 áreas de domínio (Início/Execução/Consultas/Dotação/Aprovações/Assinatura).
 * Início/Dotação/Assinatura ficam de fora aqui porque `AppRoutes.tsx` real não tem rota pra
 * eles ainda (nav não aponta pra tela que não existe — mesma disciplina de honestidade sobre
 * gap já usada nos banners de Alerta). A proposta da Paula também sugeria mover
 * liquidação/pagamento/trilha pra ações de linha dentro da lista de empenhos — não adotado
 * aqui: exigiria editar `ExecucaoPage.tsx`, e o único ponto de encaixe desta issue é o
 * `PageLayout`/nav em si. Em vez disso, os 7 destinos de sempre continuam todos alcançáveis,
 * como nível secundário da área ativa.
 */
const AREAS: readonly NavArea[] = [
  {
    id: 'execucao',
    label: 'Execução',
    items: [
      { to: '/execucao', label: 'Execução orçamentária' },
      { to: '/execucao/liquidacoes', label: 'Registrar liquidação' },
      { to: '/execucao/pagamentos', label: 'Registrar pagamento' },
    ],
  },
  {
    id: 'aprovacoes',
    label: 'Aprovações',
    items: [{ to: '/execucao/aprovacoes', label: 'Fila de aprovação' }],
  },
  {
    id: 'consultas',
    label: 'Consultas',
    items: [
      { to: '/razao/saldo', label: 'Saldo por conta' },
      { to: '/razao/balancete', label: 'Balancete' },
      { to: '/razao/contas', label: 'Catálogo de contas' },
    ],
  },
];

/**
 * `Link`, não `NavLink`: o botão primário de uma área precisa ficar ativo quando QUALQUER um
 * dos seus itens (rotas irmãs) casa com a rota atual — o `isActive`/`aria-current` embutido do
 * `NavLink` só sabe comparar contra o próprio `to`, não contra um grupo de rotas.
 */

/** `/execucao/liquidacoes/:id/trilha` (RAZ-143) é a única rota que aninha sob um item de nav. */
function itemEstaAtivo(item: NavItem, pathname: string) {
  return pathname === item.to || (item.to === '/execucao/liquidacoes' && pathname.startsWith(`${item.to}/`));
}

const linkStyle = (ativo: boolean) => ({
  color: ativo ? 'var(--color-brand-default)' : 'var(--color-text-secondary)',
  fontWeight: ativo ? 'var(--typography-weight-bold)' : 'var(--typography-weight-regular)',
  textDecoration: 'none',
});

export function FeatureNav() {
  const { pathname } = useLocation();
  const areaAtiva = AREAS.find((area) => area.items.some((item) => itemEstaAtivo(item, pathname)));

  return (
    <div style={{ marginBottom: 'var(--spacing-lg)' }}>
      <nav
        aria-label="Navegação principal"
        style={{
          display: 'flex',
          gap: 'var(--spacing-lg)',
          borderBottom: '1px solid var(--color-border-default)',
          paddingBottom: 'var(--spacing-sm)',
        }}
      >
        {AREAS.map((area) => {
          const ativa = area === areaAtiva;
          return (
            <Link key={area.id} to={area.items[0].to} aria-current={ativa ? 'page' : undefined} style={linkStyle(ativa)}>
              {area.label}
            </Link>
          );
        })}
      </nav>

      {areaAtiva && areaAtiva.items.length > 1 && (
        <nav
          aria-label={`Navegação de ${areaAtiva.label}`}
          style={{ display: 'flex', gap: 'var(--spacing-md)', marginTop: 'var(--spacing-sm)' }}
        >
          {areaAtiva.items.map((item) => {
            const ativo = itemEstaAtivo(item, pathname);
            return (
              <Link
                key={item.to}
                to={item.to}
                aria-current={ativo ? 'page' : undefined}
                style={{ ...linkStyle(ativo), fontSize: 'var(--typography-size-sm)' }}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
      )}
    </div>
  );
}
