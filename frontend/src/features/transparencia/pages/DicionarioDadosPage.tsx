/**
 * Tela 3 — Dicionário de dados. Renderiza `GET /dicionario-dados` (UX-5/ADR-0059): mesma
 * fonte da API, não um texto à parte. O campo `função` documentado abaixo do markdown é uma
 * descrição de PRODUTO (não do backend) sinalizando explicitamente que o texto-fonte da API
 * ainda não descreve esse filtro — gap 2 do ADR-0060, não escondido nem fabricado como se a
 * API já documentasse.
 */
import { Link, useParams } from 'react-router-dom';
import { PortalLayout } from '../components/PortalLayout';
import { DicionarioMarkdown } from '../components/DicionarioMarkdown';
import { useDicionarioDados } from '../api/useDicionarioDados';

export function DicionarioDadosPage() {
  const { enteId = '' } = useParams<{ enteId: string }>();
  const { data, isLoading, isError } = useDicionarioDados(enteId);

  return (
    <PortalLayout enteId={enteId}>
      <nav aria-label="Trilha de navegação" style={{ fontSize: 'var(--typography-size-sm)', marginBottom: 'var(--spacing-md)' }}>
        <Link to={`/transparencia/${enteId}`} style={{ color: 'var(--color-text-link)' }}>
          Transparência
        </Link>{' '}
        <span aria-hidden="true">›</span> <span style={{ color: 'var(--color-text-secondary)' }}>Dicionário de dados</span>
      </nav>

      <h1>Dicionário de dados da transparência ativa</h1>
      <p style={{ fontSize: 'var(--typography-size-sm)', color: 'var(--color-text-tertiary)', maxWidth: 900 }}>
        Conteúdo renderizado a partir de <code>GET /transparencia/{enteId}/dicionario-dados</code> (<code>text/markdown</code> —
        mesma fonte servida pela API, não um texto reescrito à parte).
      </p>

      {isLoading && <p role="status">Consultando dicionário de dados…</p>}
      {isError && (
        <p role="alert" style={{ color: 'var(--color-state-danger-fg)' }}>
          Não foi possível consultar o dicionário de dados.
        </p>
      )}
      {data && <DicionarioMarkdown markdown={data} />}

      <section aria-labelledby="funcao-titulo" style={{ marginTop: 'var(--spacing-lg)' }}>
        <h2 id="funcao-titulo">função</h2>
        <p>
          Campo de filtro aceito pela API (<code>funcao</code>), mas ainda NÃO descrito no texto-fonte do dicionário acima —
          gap real de backend, sinalizado aqui em vez de inventado como se já estivesse documentado. Espera-se representar a
          função de governo do gasto (classificação orçamentária), mas a descrição definitiva depende de quem mantém o
          endpoint (RAZ-273/backend).
        </p>
      </section>
    </PortalLayout>
  );
}
