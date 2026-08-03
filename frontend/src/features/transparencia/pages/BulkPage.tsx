/**
 * Tela 4 — Baixar base completa (bulk, CDN). `GET /despesas/bulk` devolve só o MANIFESTO
 * (formato/versão/contagem/última atualização + link); o arquivo em si é servido por CDN, não
 * pela API interativa (ADR-0059 D4). Alerta explícito: este arquivo NÃO é filtrado pelos
 * critérios da Tela 1 — o nome do arquivo CDN é função de `enteId`+`versao`, não de parâmetros
 * de filtro (confirmado no código do controller).
 */
import type { ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert } from '@siafic/design-system';
import { transparenciaClient } from '../../../shared/api/transparenciaClient';
import { PortalLayout } from '../components/PortalLayout';
import { formatarInstanteComHora } from '../domain/formatoExibicao';
import { useManifestoBulk } from '../api/useManifestoBulk';

export function BulkPage() {
  const { enteId = '' } = useParams<{ enteId: string }>();
  const { data, isLoading, isError } = useManifestoBulk(enteId);

  return (
    <PortalLayout enteId={enteId}>
      <h1>Baixar base completa de despesas</h1>
      <p style={{ fontSize: 'var(--typography-size-sm)', color: 'var(--color-text-tertiary)', maxWidth: 900 }}>
        Acionado a partir de "Baixar base completa (CSV)" na lista de despesas — <code>GET /despesas/bulk</code> devolve o
        manifesto; o arquivo real é servido por CDN, não pela API interativa (ADR-0059 D4).
      </p>

      <div style={{ maxWidth: 428, margin: 'var(--spacing-md) 0' }}>
        <Alert level="warning">
          <Alert.Title>Este arquivo não é filtrado</Alert.Title>
          <Alert.Body>
            A base completa contém TODOS os registros públicos deste ente, independentemente dos filtros aplicados na lista
            (estágio/data/órgão/etc.). Para um recorte filtrado, use "Exportar CSV desta consulta" na própria lista.
          </Alert.Body>
        </Alert>
      </div>

      {isLoading && <p role="status">Consultando manifesto de download…</p>}
      {isError && (
        <p role="alert" style={{ color: 'var(--color-state-danger-fg)' }}>
          Não foi possível consultar o manifesto de download.
        </p>
      )}

      {data && (
        <div style={{ background: 'var(--color-bg-surface)', border: '1px solid var(--color-border-default)', borderRadius: 'var(--radius-md)', padding: 'var(--spacing-xl)', display: 'flex', flexDirection: 'column', gap: 'var(--spacing-md)', maxWidth: 700 }}>
          <div style={{ display: 'flex', gap: 'var(--spacing-xl)', flexWrap: 'wrap' }}>
            <Campo rotulo="Formato">{data.formato.toUpperCase()}</Campo>
            <Campo rotulo="Versão do arquivo">{data.versao}</Campo>
            <Campo rotulo="Contagem aproximada">{data.contagemAproximada.toLocaleString('pt-BR')} registros</Campo>
            <Campo rotulo="Última atualização">{data.ultimaAtualizacao ? formatarInstanteComHora(data.ultimaAtualizacao) : '—'}</Campo>
          </div>
          <div>
            <a href={transparenciaClient.arquivoBulkUrl(data.arquivoCdn)}>Baixar CSV (arquivo completo, servido por CDN)</a>
          </div>
          <p style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>
            Endereço: <code>{data.arquivoCdn}</code> — cacheável, absorve pico sem exigir cadastro (Decreto 10.540 art. 9º).
          </p>
        </div>
      )}

      <div style={{ maxWidth: 428, margin: 'var(--spacing-lg) 0' }}>
        <Alert level="info">
          <Alert.Title>Atualização periódica</Alert.Title>
          <Alert.Body>
            A versão do arquivo muda quando o read model é atualizado; o carimbo "versão" identifica exatamente qual corte
            dos dados você baixou (rastreabilidade/LAI art. 8º).
          </Alert.Body>
        </Alert>
      </div>

      <Link to={`/transparencia/${enteId}`}>‹ Voltar à lista de despesas</Link>
    </PortalLayout>
  );
}

const rotuloCampoStyle = {
  margin: 0,
  fontSize: '0.75rem',
  fontWeight: 'var(--typography-weight-medium)',
  color: 'var(--color-text-secondary)',
  textTransform: 'uppercase' as const,
  letterSpacing: '0.4px',
};

function Campo({ rotulo, children }: { rotulo: string; children: ReactNode }) {
  return (
    <div>
      <p style={rotuloCampoStyle}>{rotulo}</p>
      <p style={{ margin: 0 }}>{children}</p>
    </div>
  );
}
