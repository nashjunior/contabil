/**
 * Prova fim-a-fim (RAZ-191): login dev -> Tela 1 (Saldo por conta) busca a conta via
 * `ContaPicker` (combobox assíncrono real, GET /razao/contas) -> seleciona -> saldo real
 * (GET /razao/saldo) aparece -> navega para Tela 2 (Balancete, GET /razao/balancete real,
 * indicador "Confere") -> navega para Tela 4 (Catálogo de contas, busca por prefixo de
 * código). Cobre o gap fechado por esta issue: `razaoClient` tinha zero consumidores no
 * front (contrato pronto desde RAZ-123, telas ausentes).
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../../../shared/auth/AuthContext';
import { AppRoutes } from '../../../app/AppRoutes';
import { server } from '../../../shared/api/mocks/server';

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

async function login(user: ReturnType<typeof userEvent.setup>) {
  expect(await screen.findByRole('heading', { name: /entrar/i })).toBeInTheDocument();
  await user.type(screen.getByLabelText('CPF'), '12345678900');
  await user.click(screen.getByRole('button', { name: /entrar/i }));
}

describe('fluxo razão — saldo por conta + balancete + catálogo de contas', () => {
  it('consulta saldo real via ContaPicker, navega ao balancete (confere) e busca no catálogo', async () => {
    const user = userEvent.setup();
    renderApp('/razao/saldo');

    await login(user);
    expect(await screen.findByRole('heading', { name: /saldo por conta/i })).toBeInTheDocument();

    // ContaPicker (RAZ-142): abre o dropdown, sintética visível-mas-não-selecionável, escolhe analítica.
    await user.click(screen.getByRole('combobox', { name: /conta contábil/i }));
    expect(await screen.findByRole('option', { name: /ativo circulante/i })).toHaveAttribute('aria-disabled', 'true');
    await user.click(await screen.findByRole('option', { name: /caixa e equivalentes de caixa/i }));

    // GET /razao/saldo real (mock explícito, não inventado) — RAZ-101/ADR-0030.
    expect(await screen.findByText('R$ 15.320,50')).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: 'Balancete' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Balancete' })).toBeInTheDocument();

    // GET /razao/balancete real: linha da conta Caixa + indicador Confere (Σdébito=Σcrédito).
    expect(await screen.findByText('R$ 1.300,00')).toBeInTheDocument();
    expect(screen.getByText(/^Confere — Σdébito = Σcrédito$/)).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: 'Catálogo de contas' }));
    expect(await screen.findByRole('heading', { name: /catálogo de contas pcasp/i })).toBeInTheDocument();
    expect(await screen.findByText('Passivo')).toBeInTheDocument();

    // GET /razao/contas?busca=1.1 real — filtra por prefixo de código, sem paginação numerada.
    await user.type(screen.getByLabelText(/código ou descrição/i), '1.1');
    await screen.findByText('Créditos a curto prazo');
    expect(screen.queryByText('Passivo')).not.toBeInTheDocument();
    expect(screen.getByText('Ativo circulante')).toBeInTheDocument();
  });
});
