/**
 * Tela 2 — Drill-down do documento. GAP REAL, não invenção de frontend: o contrato
 * (`TransparenciaPublicaController`) não tem um `GET /despesas/{sequencia}` — só lista,
 * totalização, bulk e dicionário. Esta tela não fabrica esse endpoint: o item chega via
 * `state` da navegação (`LinhaResultado`, que já tem o item carregado da Tela 1). Um acesso
 * direto a esta URL (link externo, atualizar a página) não tem como se resolver sozinho —
 * mostra uma mensagem honesta em vez de simular um endpoint que não existe.
 *
 * "‹ Voltar"/"Despesas (filtros da consulta preservados)" usam `navigate(-1)`: preserva os
 * filtros ativos exatamente como o histórico do navegador já os guarda, sem duplicar a
 * serialização de filtro só para reconstruir o link.
 */
import type { ReactNode } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import type { ItemTransparencia } from '../../../shared/api/transparenciaClient';
import { PortalLayout } from '../components/PortalLayout';
import { BadgeEstagio } from '../components/BadgeEstagio';
import { ValorMonetario } from '../components/ValorMonetario';
import {
  beneficiarioDoPayload,
  contratoIdDoPayload,
  credorIdDoPayload,
  dataDoPayload,
  estagioDoPayload,
  historicoDoPayload,
  numeroEmpenhoDoPayload,
  orgaoIdDoPayload,
  valorDoPayload,
} from '../domain/payloadDespesa';
import { formatarDataIso, formatarInstanteComHora } from '../domain/formatoExibicao';

const SEM_DADO = '— (não disponível para este estágio)';

export function DetalhePage() {
  const { enteId = '', sequencia = '' } = useParams<{ enteId: string; sequencia: string }>();
  const location = useLocation();
  const navigate = useNavigate();

  const item = (location.state as { item?: ItemTransparencia } | null)?.item;

  if (!item || String(item.sequencia) !== sequencia) {
    return (
      <PortalLayout enteId={enteId}>
        <h1>Documento não encontrado nesta sessão</h1>
        <p>
          A API pública de transparência não tem uma consulta individual por número de sequência — este detalhe só existe a
          partir de um clique vindo da lista. Volte à lista de despesas e abra o documento novamente.
        </p>
        <Link to={`/transparencia/${enteId}`}>‹ Voltar à lista de despesas</Link>
      </PortalLayout>
    );
  }

  const { payload } = item;
  const estagio = estagioDoPayload(payload);
  const data = dataDoPayload(payload);
  const valor = valorDoPayload(payload);
  const numeroEmpenho = numeroEmpenhoDoPayload(payload);
  const credorId = credorIdDoPayload(payload);
  const orgaoId = orgaoIdDoPayload(payload);
  const contratoId = contratoIdDoPayload(payload);
  const historico = historicoDoPayload(payload);
  const beneficiario = beneficiarioDoPayload(payload);
  const rotulo = numeroEmpenho ? `Empenho nº ${numeroEmpenho}` : `Documento #${item.sequencia}`;

  return (
    <PortalLayout enteId={enteId}>
      <nav aria-label="Trilha de navegação" style={{ fontSize: 'var(--typography-size-sm)', marginBottom: 'var(--spacing-md)' }}>
        <Link to={`/transparencia/${enteId}`} style={{ color: 'var(--color-text-link)' }}>
          Transparência
        </Link>{' '}
        <span aria-hidden="true">›</span>{' '}
        <button
          type="button"
          onClick={() => navigate(-1)}
          style={{ background: 'transparent', border: 'none', padding: 0, color: 'var(--color-text-link)', fontSize: 'inherit', cursor: 'pointer' }}
        >
          Despesas (filtros da consulta preservados)
        </button>{' '}
        <span aria-hidden="true">›</span> <span style={{ color: 'var(--color-text-secondary)' }}>{rotulo}</span>
      </nav>

      <div style={{ background: 'var(--color-bg-surface)', border: '1px solid var(--color-border-default)', borderRadius: 'var(--radius-md)', padding: 'var(--spacing-xl)', display: 'flex', flexDirection: 'column', gap: 'var(--spacing-md)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-md)' }}>
          <h1 style={{ margin: 0 }}>{rotulo}</h1>
          <BadgeEstagio estagio={estagio} />
        </div>

        <div style={{ display: 'flex', gap: 'var(--spacing-xl)', flexWrap: 'wrap' }}>
          <Campo rotulo="Publicado em">{formatarInstanteComHora(item.publicadoEm)}</Campo>
          <Campo rotulo="Publicar até (SLA)">{formatarInstanteComHora(item.publicarAte)} (1º dia útil — Decreto 10.540 art. 3º)</Campo>
          <Campo rotulo="Sequência (read model)">{item.sequencia}</Campo>
        </div>

        {valor && (
          <div>
            <ValorMonetario valor={valor} enfase="forte" />
          </div>
        )}

        <div style={{ display: 'flex', gap: 'var(--spacing-xl)', flexWrap: 'wrap' }}>
          <Campo rotulo="Data">{data ? formatarDataIso(data) : SEM_DADO}</Campo>
          <Campo rotulo="Nº do empenho">{numeroEmpenho ?? SEM_DADO}</Campo>
          <Campo rotulo="Órgão">{orgaoId ?? SEM_DADO}</Campo>
          <Campo rotulo="Contrato">{contratoId ?? '— (não vinculado a contrato)'}</Campo>
        </div>

        <div>
          <p style={{ ...rotuloCampoStyle }}>Credor</p>
          {beneficiario?.nome ? (
            <p style={{ margin: 0 }}>
              {beneficiario.nome} {beneficiario.documento ? `· ${beneficiario.documento}` : ''}
            </p>
          ) : credorId ? (
            <p style={{ margin: 0, fontFamily: 'monospace' }}>{credorId}</p>
          ) : (
            <p style={{ margin: 0, color: 'var(--color-text-tertiary)' }}>{SEM_DADO}</p>
          )}
          <p style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)', marginTop: 'var(--spacing-2xs)' }}>
            CNPJ íntegro é permitido; pessoa física aparece com CPF já mascarado (***.456.***-**) pelo backend. Credor/órgão
            são identificadores opacos — a API não expõe nome/busca por nome (ver Dicionário de dados).
          </p>
        </div>

        {historico && (
          <div>
            <p style={rotuloCampoStyle}>Histórico</p>
            <p style={{ margin: 0 }}>{historico}</p>
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--spacing-xs)', marginTop: 'var(--spacing-sm)' }}>
          {credorId && (
            <Link to={`/transparencia/${enteId}?credorId=${encodeURIComponent(credorId)}`}>Ver todas as despesas deste credor ›</Link>
          )}
          {orgaoId && <Link to={`/transparencia/${enteId}?orgaoId=${encodeURIComponent(orgaoId)}`}>Ver todas as despesas deste órgão ›</Link>}
          <details>
            <summary style={{ cursor: 'pointer', color: 'var(--color-text-link)' }}>Ver JSON bruto deste registro (dados abertos, LAI art. 8º)</summary>
            <pre style={{ background: 'var(--color-bg-inset)', padding: 'var(--spacing-md)', borderRadius: 'var(--radius-sm)', overflowX: 'auto' }}>
              {JSON.stringify(item, null, 2)}
            </pre>
          </details>
          <button type="button" onClick={() => navigate(-1)} style={{ background: 'transparent', color: 'var(--color-text-link)', border: 'none', padding: 0, alignSelf: 'flex-start' }}>
            ‹ Voltar à lista de despesas
          </button>
        </div>
      </div>
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
