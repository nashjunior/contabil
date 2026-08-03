/**
 * Shell público do Portal da Transparência (RAZ-290/ADR-0060 decisão 1): header + rodapé
 * institucionais NOVOS, que não reusam `PageLayout`/`FeatureNav`/`Seletor de Ente` do
 * back-office — essa superfície não tem sessão (`RequireAuth` nunca envolve estas rotas) nem
 * troca de ente interativa (o ente já é o path `/transparencia/{enteId}`). Espelha
 * `Header Institucional (Público)`/`Rodapé Institucional (Público)` do Figma (fileKey
 * ObQu8oMQ0cEGbONMXgpuLU, nodes 196:3/196:15) — texto e estrutura extraídos ao vivo via
 * `get_design_context`, não aproximados de memória.
 *
 * Skip link, breadcrumb e landmarks são as únicas âncoras de navegação persistente (sem nav
 * principal do operador) — a11y: link "Pular para o conteúdo" só some via `sr-only` quando o
 * teclado não está focado nele (eMAG/WCAG 2.2 AA).
 */
import { useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';

export function PortalLayout({ enteId, children }: { enteId: string; children: ReactNode }) {
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <PortalHeader enteId={enteId} />
      <main id="conteudo-principal" style={{ flex: 1, maxWidth: 1216, width: '100%', margin: '0 auto', padding: 'var(--spacing-xl) var(--spacing-xl) var(--spacing-3xl)' }}>
        {children}
      </main>
      <PortalFooter enteId={enteId} />
    </div>
  );
}

function SkipLink() {
  const [focado, setFocado] = useState(false);
  return (
    <a
      href="#conteudo-principal"
      onFocus={() => setFocado(true)}
      onBlur={() => setFocado(false)}
      className={focado ? undefined : 'sr-only'}
      style={{ color: 'var(--color-text-link)', fontSize: 'var(--typography-size-sm)' }}
    >
      Pular para o conteúdo
    </a>
  );
}

function PortalHeader({ enteId }: { enteId: string }) {
  return (
    <header
      style={{
        background: 'var(--color-bg-surface)',
        borderBottom: '1px solid var(--color-border-default)',
        padding: 'var(--spacing-md) var(--spacing-xl) var(--spacing-lg)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--spacing-sm)',
      }}
    >
      <SkipLink />
      <div style={{ display: 'flex', gap: 'var(--spacing-md)', alignItems: 'center' }}>
        <div aria-hidden="true" style={{ width: 40, height: 40, borderRadius: 'var(--radius-md)', background: 'var(--color-bg-inset)', flexShrink: 0 }} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <p style={{ margin: 0, fontSize: 'var(--typography-size-xl)', fontWeight: 'var(--typography-weight-bold)', color: 'var(--color-text-primary)' }}>
            Portal da Transparência
          </p>
          <p style={{ margin: 0, fontSize: 'var(--typography-size-sm)', color: 'var(--color-text-secondary)' }}>
            SIAFIC · execução orçamentário-financeira (Decreto 10.540/2020)
          </p>
        </div>
        <p style={{ margin: 0, fontSize: 'var(--typography-size-sm)', color: 'var(--color-text-tertiary)', textAlign: 'right' }}>
          gov.br · VLibras (piso WCAG 2.2 AA/eMAG — símbolo/VLibras: pendente)
        </p>
      </div>
      <nav aria-label="Trilha de navegação" style={{ display: 'flex', gap: 'var(--spacing-2xs)', fontSize: 'var(--typography-size-sm)' }}>
        <Link to={`/transparencia/${enteId}`} style={{ color: 'var(--color-text-link)' }}>
          Transparência
        </Link>
        <span aria-hidden="true" style={{ color: 'var(--color-text-tertiary)' }}>
          ›
        </span>
        <span style={{ color: 'var(--color-text-secondary)' }}>Ente {enteId} — nome não exposto pelo contrato</span>
      </nav>
    </header>
  );
}

function PortalFooter({ enteId }: { enteId: string }) {
  return (
    <footer style={{ background: 'var(--color-bg-inset)', padding: 'var(--spacing-lg) var(--spacing-xl)', display: 'flex', flexDirection: 'column', gap: 'var(--spacing-sm)' }}>
      <div style={{ display: 'flex', gap: 'var(--spacing-lg)', flexWrap: 'wrap', fontSize: 'var(--typography-size-sm)' }}>
        <Link to={`/transparencia/${enteId}/dicionario-dados`} style={{ color: 'var(--color-text-link)' }}>
          Dicionário de dados
        </Link>
        <span style={{ color: 'var(--color-text-secondary)' }}>Dados abertos: JSON · CSV</span>
        <span style={{ color: 'var(--color-text-tertiary)' }}>Declaração de acessibilidade (pendente)</span>
      </div>
      <p style={{ margin: 0, fontSize: 'var(--typography-size-sm)', color: 'var(--color-text-tertiary)', maxWidth: 900 }}>
        Fonte: read model público da transparência ativa (ADR-0059/RAZ-273) — LRF art. 48/48-A, Decreto 10.540/2020 art.
        3º/9º, LAI 12.527/2011 art. 8º. Publicado até o 1º dia útil subsequente ao fato.
      </p>
    </footer>
  );
}
