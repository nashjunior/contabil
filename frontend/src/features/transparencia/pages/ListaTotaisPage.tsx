/**
 * Tela 1 (Lista) + Tela 1b (Totais) — a MESMA tela, alternada por aba (ADR-0060 decisão 3):
 * um único componente `BarraFiltros` compartilhado entre as duas visões (UX-2/ADR-0059), com
 * o filtro inteiro vivendo na URL (`useSearchParams`) para o drill-down totalização→lista
 * (Tela 1b → Tela 1, aplicando o `estagio` clicado e preservando os demais filtros) navegar
 * sem duplicar estado. "Carregar mais" (não paginação numerada — UX-4, keyset) e a contagem
 * de resultados em região `aria-live="polite"`.
 */
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { transparenciaClient } from '../../../shared/api/transparenciaClient';
import { PortalLayout } from '../components/PortalLayout';
import { BarraFiltros } from '../components/BarraFiltros';
import { LinhaResultado } from '../components/LinhaResultado';
import { LinhaTotalizacao } from '../components/LinhaTotalizacao';
import { ValorMonetario } from '../components/ValorMonetario';
import { useDespesasPublicas } from '../api/useDespesasPublicas';
import { useTotalizacoesPublicas } from '../api/useTotalizacoesPublicas';
import { aplicarFiltroEmSearchParams, filtroDeSearchParams, filtroParaApi, limparFiltroEmSearchParams, type FiltroFormState } from '../domain/filtro';
import { formatarInstanteComHora } from '../domain/formatoExibicao';

type Aba = 'lista' | 'totais';

export function ListaTotaisPage() {
  const { enteId = '' } = useParams<{ enteId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();

  const aba: Aba = searchParams.get('aba') === 'totais' ? 'totais' : 'lista';
  const filtroForm = filtroDeSearchParams(searchParams);
  const filtroApi = filtroParaApi(filtroForm);

  function handleAplicar(novoFiltro: FiltroFormState) {
    setSearchParams(aplicarFiltroEmSearchParams(searchParams, novoFiltro));
  }

  function handleLimpar() {
    setSearchParams(limparFiltroEmSearchParams(searchParams));
  }

  function handleTrocarAba(novaAba: Aba) {
    const proximos = new URLSearchParams(searchParams);
    proximos.set('aba', novaAba);
    setSearchParams(proximos);
  }

  function handleDrillDown(estagio: string) {
    const proximos = aplicarFiltroEmSearchParams(searchParams, { ...filtroForm, estagio });
    proximos.set('aba', 'lista');
    setSearchParams(proximos);
  }

  const despesas = useDespesasPublicas(enteId, filtroApi);
  const totais = useTotalizacoesPublicas(enteId, filtroApi, { enabled: aba === 'totais' });

  const itens = despesas.data?.pages.flatMap((pagina) => pagina.itens) ?? [];
  const ultimaPagina = despesas.data?.pages.at(-1);

  return (
    <PortalLayout enteId={enteId}>
      <h1>Despesas públicas</h1>

      <div role="tablist" aria-label="Visualização" style={{ display: 'flex', gap: 'var(--spacing-md)', borderBottom: '1px solid var(--color-border-default)', marginBottom: 'var(--spacing-md)' }}>
        <button
          type="button"
          role="tab"
          aria-selected={aba === 'lista'}
          onClick={() => handleTrocarAba('lista')}
          style={{ background: 'transparent', color: aba === 'lista' ? 'var(--color-text-link)' : 'var(--color-text-secondary)', border: 'none', borderBottom: aba === 'lista' ? '2px solid var(--color-brand-default)' : '2px solid transparent', borderRadius: 0, padding: 'var(--spacing-sm) var(--spacing-md)' }}
        >
          Lista
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={aba === 'totais'}
          onClick={() => handleTrocarAba('totais')}
          style={{ background: 'transparent', color: aba === 'totais' ? 'var(--color-text-link)' : 'var(--color-text-secondary)', border: 'none', borderBottom: aba === 'totais' ? '2px solid var(--color-brand-default)' : '2px solid transparent', borderRadius: 0, padding: 'var(--spacing-sm) var(--spacing-md)' }}
        >
          Totais — mesmo filtro
        </button>
      </div>
      <p style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>
        As duas abas leem a mesma barra de filtros abaixo — trocar de aba não reseta nem duplica filtro.
      </p>

      <div style={{ margin: 'var(--spacing-md) 0' }}>
        <BarraFiltros key={JSON.stringify(filtroForm)} valor={filtroForm} onAplicar={handleAplicar} onLimpar={handleLimpar} />
      </div>

      {aba === 'lista' ? (
        <section role="tabpanel" aria-label="Lista de despesas" style={{ marginTop: 'var(--spacing-lg)' }}>
          {despesas.isLoading && <p role="status">Consultando despesas públicas…</p>}
          {despesas.isError && (
            <p role="alert" style={{ color: 'var(--color-state-danger-fg)' }}>
              Não foi possível consultar as despesas públicas.
            </p>
          )}
          {ultimaPagina && (
            <p aria-live="polite" style={{ fontSize: 'var(--typography-size-sm)', color: 'var(--color-text-secondary)' }}>
              Mostrando {itens.length} de aproximadamente {ultimaPagina.contagemAproximada.toLocaleString('pt-BR')} registros públicos
            </p>
          )}

          {itens.length > 0 && (
            <table>
              <caption className="sr-only">Lista de despesas públicas</caption>
              <thead>
                <tr>
                  <th scope="col">Estágio</th>
                  <th scope="col">Data</th>
                  <th scope="col">Valor</th>
                  <th scope="col">Credor</th>
                  <th scope="col">Órgão</th>
                  <th scope="col">Nº empenho</th>
                  <th scope="col">Publicado em</th>
                  <th scope="col">
                    <span className="sr-only">Detalhes</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {itens.map((item) => (
                  <LinhaResultado key={`${item.tipoEvento}-${item.sequencia}`} enteId={enteId} item={item} />
                ))}
              </tbody>
            </table>
          )}

          {!despesas.isLoading && itens.length === 0 && <p>Nenhuma despesa pública encontrada para os filtros aplicados.</p>}

          {despesas.hasNextPage && (
            <p aria-live="polite" style={{ marginTop: 'var(--spacing-md)' }}>
              <button type="button" onClick={() => despesas.fetchNextPage()} disabled={despesas.isFetchingNextPage}>
                {despesas.isFetchingNextPage ? 'Carregando…' : 'Carregar mais'}
              </button>{' '}
              <span style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>
                paginação por cursor opaco — sem numeração de página
              </span>
            </p>
          )}

          <div style={{ marginTop: 'var(--spacing-lg)', display: 'flex', gap: 'var(--spacing-lg)', alignItems: 'baseline', flexWrap: 'wrap' }}>
            <a href={transparenciaClient.exportarCsvUrl(enteId, filtroApi)}>Exportar CSV desta consulta ↓</a>
            <Link to={`/transparencia/${enteId}/bulk`}>Baixar base completa (CSV) ›</Link>
            <span style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>
              a base completa NÃO aplica os filtros acima
            </span>
          </div>
        </section>
      ) : (
        <section role="tabpanel" aria-label="Totais por estágio" style={{ marginTop: 'var(--spacing-lg)' }}>
          {totais.isLoading && <p role="status">Consultando totalizações…</p>}
          {totais.isError && (
            <p role="alert" style={{ color: 'var(--color-state-danger-fg)' }}>
              Não foi possível consultar as totalizações.
            </p>
          )}
          {totais.data && (
            <>
              <table>
                <caption className="sr-only">Totais por estágio</caption>
                <thead>
                  <tr>
                    <th scope="col">Estágio</th>
                    <th scope="col">Quantidade</th>
                    <th scope="col">Valor</th>
                    <th scope="col">
                      <span className="sr-only">Ação</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {totais.data.linhas.map((linha) => (
                    <LinhaTotalizacao key={linha.estagio} linha={linha} onVerDocumentos={handleDrillDown} />
                  ))}
                  <tr>
                    <th scope="row">Total geral</th>
                    <td>{totais.data.contagemAproximada.toLocaleString('pt-BR')} documentos</td>
                    <td>
                      <ValorMonetario valor={totais.data.totalGeral} enfase="forte" />
                    </td>
                    <td />
                  </tr>
                </tbody>
              </table>
              {totais.data.ultimaAtualizacao && (
                <p style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>
                  Última atualização: {formatarInstanteComHora(totais.data.ultimaAtualizacao)} — consistência eventual dentro do SLA de 1 dia útil.
                </p>
              )}
            </>
          )}
        </section>
      )}
    </PortalLayout>
  );
}
