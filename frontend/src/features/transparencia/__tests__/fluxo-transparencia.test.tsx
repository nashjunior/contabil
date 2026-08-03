/**
 * Prova fim-a-fim do Portal Público de Transparência (RAZ-290/ADR-0060): rota pública SEM
 * login (nenhum `login()` aqui, ao contrário de `fluxo-razao.test.tsx`) — Lista (paginação
 * "Carregar mais") -> Totais (mesma barra de filtros, UX-2) -> drill-down totalização->lista
 * preservando estágio -> Tela 2 (detalhe via `state` da navegação, sem endpoint de detalhe) ->
 * Dicionário de dados -> Baixar base completa.
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../../../shared/auth/AuthContext';
import { AppRoutes } from '../../../app/AppRoutes';
import { server } from '../../../shared/api/mocks/server';

const ENTE_ID = '55555555-5555-4555-8555-555555555555';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function renderApp(rotaInicial: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={[rotaInicial]}>
          <AppRoutes />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('portal público de transparência — lista, totais, drill-down, detalhe, dicionário, bulk', () => {
  it('não exige login e cobre o fluxo completo das 5 telas', async () => {
    const user = userEvent.setup();
    renderApp(`/transparencia/${ENTE_ID}`);

    // Tela 1 — Lista (sem RequireAuth: nenhum redirect para /entrar).
    expect(await screen.findByRole('heading', { level: 1, name: 'Despesas públicas' })).toBeInTheDocument();
    expect(await screen.findByText(/Mostrando 3 de aproximadamente 6 registros públicos/)).toBeInTheDocument();

    const tabelaLista = screen.getByRole('table', { name: 'Lista de despesas públicas' });
    expect(within(tabelaLista).getAllByRole('row')).toHaveLength(4); // 1 cabeçalho + 3 itens (limit=3 no mock)

    // "Carregar mais" (keyset, UX-4) — acumula, não substitui.
    await user.click(screen.getByRole('button', { name: /carregar mais/i }));
    expect(await screen.findByText(/Mostrando 6 de aproximadamente 6 registros públicos/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /carregar mais/i })).not.toBeInTheDocument();
    // Nº do empenho só existe no payload de estágio "empenhado" (gap real, não bug de parse).
    expect(within(tabelaLista).getByText('1024')).toBeInTheDocument();

    // Aba Totais — mesma barra de filtros da Lista (UX-2).
    await user.click(screen.getByRole('tab', { name: /totais — mesmo filtro/i }));
    expect(await screen.findByText('Total geral')).toBeInTheDocument();
    const botoesDrillDown = await screen.findAllByRole('button', { name: /ver documentos deste estágio/i });
    expect(botoesDrillDown).toHaveLength(3); // empenhado, liquidado, pago (ordem fixa do mock)

    // Drill-down totalização → lista, aplicando "liquidado" e preservando a aba/URL.
    await user.click(botoesDrillDown[1]);
    expect(await screen.findByRole('tab', { name: 'Lista', selected: true })).toBeInTheDocument();
    expect(await screen.findByText(/Mostrando 1 de aproximadamente 1 registros públicos/)).toBeInTheDocument();
    const tabelaFiltrada = screen.getByRole('table', { name: 'Lista de despesas públicas' });
    expect(within(tabelaFiltrada).getByText('Liquidado')).toBeInTheDocument();
    expect(within(tabelaFiltrada).queryByText('Empenhado')).not.toBeInTheDocument();

    // Tela 2 — drill-down do documento (sem endpoint dedicado: item chega via `state`).
    await user.click(screen.getByRole('link', { name: /ver detalhes do documento 3/i }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Documento #3' })).toBeInTheDocument();
    expect(screen.getByText('Medição 1 — pavimentação Rua das Flores')).toBeInTheDocument();
    // Gap real: liquidação não carrega credorId/orgaoId/numeroSequencial no payload.
    expect(screen.getAllByText('— (não disponível para este estágio)').length).toBeGreaterThan(0);

    // "‹ Voltar" preserva o filtro (histórico do navegador, não reconstrução de URL).
    await user.click(screen.getByRole('button', { name: /voltar à lista de despesas/i }));
    expect(await screen.findByText(/Mostrando 1 de aproximadamente 1 registros públicos/)).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Lista de despesas públicas' })).getByText('Liquidado')).toBeInTheDocument();

    // Tela 3 — Dicionário de dados (rodapé institucional).
    await user.click(screen.getByRole('link', { name: 'Dicionário de dados' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Dicionário de dados da transparência ativa' })).toBeInTheDocument();
    expect(screen.getByText(/etapa estável da execução/)).toBeInTheDocument();
    expect(screen.getByText(/ainda NÃO descrito no texto-fonte/)).toBeInTheDocument();

    // Tela 4 — Baixar base completa (bulk, CDN) — volta à lista e usa o link direto.
    // Duas âncoras "Transparência" nesta tela (header institucional + trilha da própria
    // página, ambas do wireframe) — qualquer uma leva à mesma rota.
    await user.click(screen.getAllByRole('link', { name: 'Transparência' })[0]);
    expect(await screen.findByRole('heading', { level: 1, name: 'Despesas públicas' })).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: /baixar base completa/i }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Baixar base completa de despesas' })).toBeInTheDocument();
    expect(screen.getByText('Este arquivo não é filtrado')).toBeInTheDocument();
    expect(screen.getByText('20260801091400000')).toBeInTheDocument();
    const linkCdn = screen.getByRole('link', { name: /baixar csv \(arquivo completo/i });
    expect(linkCdn).toHaveAttribute('href', expect.stringContaining(`/cdn/transparencia/${ENTE_ID}/despesas/`));
  });
});
