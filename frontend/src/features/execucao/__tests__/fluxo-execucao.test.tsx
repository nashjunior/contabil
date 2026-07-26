/**
 * Prova fim-a-fim (escopo deste sandbox — sem backend Spring Boot real
 * disponível, ver README "Gaps"): login dev -> registrar empenho contra o
 * client de API real (fetch real, interceptado pelo MSW no mesmo contrato
 * derivado do código real) -> item aparece na lista + agregado real
 * (GET /execucao/orcamentaria) reflete o valor. Cobre: enteId no path,
 * Authorization: Bearer, valor monetário preservado como texto decimal.
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../../../shared/auth/AuthContext';
import { AppRoutes } from '../../../app/AppRoutes';
import { server } from '../../../shared/api/mocks/server';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function renderApp() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={['/execucao']}>
          <AppRoutes />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

const UUID_DOTACAO = '11111111-1111-4111-8111-000000000001';
const UUID_CREDOR = '11111111-1111-4111-8111-000000000002';
const UUID_UG = '11111111-1111-4111-8111-000000000003';

describe('fluxo execução — login + registrar empenho + consultar agregado real', () => {
  it('redireciona para /entrar quando não autenticado, entra, registra e reflete no GET real e na lista', async () => {
    const user = userEvent.setup();
    renderApp();

    expect(await screen.findByRole('heading', { name: /entrar/i })).toBeInTheDocument();

    await user.type(screen.getByLabelText('CPF'), '12345678900');
    await user.click(screen.getByRole('button', { name: /entrar/i }));

    expect(await screen.findByRole('heading', { name: /execução orçamentária/i })).toBeInTheDocument();
    expect(screen.getByText('***.456.***-**', { exact: false })).toBeInTheDocument();

    // Agregado real (GET /execucao/orcamentaria) começa zerado.
    expect(await screen.findAllByText('R$ 0,00')).toHaveLength(5);

    await user.type(screen.getByLabelText(/id da dotação/i), UUID_DOTACAO);
    await user.selectOptions(screen.getByLabelText(/^tipo/i), 'ordinario');
    await user.type(screen.getByLabelText(/id do credor/i), UUID_CREDOR);
    await user.type(screen.getByLabelText(/id da unidade gestora/i), UUID_UG);
    await user.type(screen.getByLabelText(/^valor/i), '1000.50');
    await user.type(screen.getByLabelText(/data do fato/i), '2026-01-15');
    await user.clear(screen.getByLabelText(/exercício/i));
    await user.type(screen.getByLabelText(/exercício/i), String(new Date().getFullYear()));
    await user.type(screen.getByLabelText(/classificação orçamentária/i), '3.3.90.30');
    await user.type(screen.getByLabelText(/fonte de recurso/i), '0100');
    await user.type(screen.getByLabelText(/histórico/i), 'Compra de material de expediente');
    await user.click(screen.getByRole('button', { name: /registrar empenho/i }));

    await waitFor(() => expect(screen.getByText('Compra de material de expediente')).toBeInTheDocument());
    expect(screen.getByRole('cell', { name: 'Ordinário' })).toBeInTheDocument();

    // O GET real invalidado pela mutation reflete o novo total empenhado — aparece no
    // resumo (empenhado + saldo a liquidar, mesmo valor pois nada foi liquidado ainda)
    // e na linha da tabela de empenhos desta sessão.
    await waitFor(() => expect(screen.getAllByText('R$ 1.000,50')).toHaveLength(3));
  });
});
