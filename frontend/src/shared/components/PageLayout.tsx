/**
 * Shell das telas autenticadas: `<main>` + cabeçalho com identificação da sessão +
 * navegação. Extraído das 4 páginas que o repetiam literalmente — a lacuna que o
 * `FeatureNav` registrava desde a RAZ-191 ("sem layout/shell compartilhado ainda").
 *
 * <p>Efeito colateral que interessa à LGPD: o CPF mascarado passa a ser renderizado
 * num ÚNICO ponto (`SessaoBadge`). O mascaramento na fronteira tem um só lugar para
 * auditar, em vez de uma cópia por página que pode divergir.
 */
import type { ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';
import { FeatureNav } from './FeatureNav';

/** As duas larguras em uso: telas de formulário e telas de tabela. */
const LARGURAS = { padrao: 720, ampla: 960 } as const;

export type LarguraPagina = keyof typeof LARGURAS;

export function PageLayout({
  titulo,
  largura = 'padrao',
  children,
}: {
  titulo: string;
  largura?: LarguraPagina;
  children: ReactNode;
}) {
  return (
    <main style={{ maxWidth: LARGURAS[largura], margin: '0 auto', padding: 'var(--spacing-xl)' }}>
      <header
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'baseline',
          marginBottom: 'var(--spacing-lg)',
        }}
      >
        <h1>{titulo}</h1>
        <SessaoBadge />
      </header>

      <FeatureNav />

      {children}
    </main>
  );
}

/**
 * Identificação do ente + CPF mascarado da sessão, com a saída. Não renderiza nada
 * sem sessão — o CPF nunca aparece fora de uma sessão autenticada.
 *
 * `cpfMascarado` já chega mascarado do `AuthContext` (do `GET /sessao/atual` ou do
 * `maskCpf` do form de dev); este componente nunca vê o CPF em claro.
 */
export function SessaoBadge() {
  const { sessao, sair } = useAuth();
  if (!sessao) return null;

  return (
    <p>
      {sessao.enteNome ?? sessao.enteId} · CPF {sessao.cpfMascarado}{' '}
      <button type="button" onClick={sair}>
        Sair
      </button>
    </p>
  );
}
