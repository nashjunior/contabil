/**
 * Prova fim-a-fim do esqueleto de rotas do RAZ-221/ADR-0052: login dev -> fila de aprovação
 * (GET /execucao/liquidacoes real) lista o item pendente -> "Ver trilha" navega para a trilha
 * dedicada (GET /execucao/liquidacoes/{id}/trilha real) -> volta para a fila. Cobre só o lado
 * de leitura (esqueleto desta entrega); os formulários de registro (liquidação/pagamento) e a
 * ação de decisão do gate ficam para a issue-filha de implementação de tela (dono: Bruno).
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

describe('fluxo aprovação — fila de aprovação (gate 4-eyes) + trilha', () => {
  it('lista a fila pendente real e navega para a trilha dedicada de um item', async () => {
    const user = userEvent.setup();
    renderApp('/execucao/aprovacoes');

    expect(await screen.findByRole('heading', { name: /entrar/i })).toBeInTheDocument();
    await user.type(screen.getByLabelText('CPF'), '12345678900');
    await user.click(screen.getByRole('button', { name: /entrar/i }));

    expect(await screen.findByRole('heading', { name: /fila de aprovação/i })).toBeInTheDocument();

    // GET /execucao/liquidacoes?statusAprovacao=pendente real — só o item pendente do mock aparece.
    expect(await screen.findByText('R$ 1.500,00')).toBeInTheDocument();
    expect(screen.queryByText('R$ 820,75')).not.toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: /ver trilha/i }));

    expect(await screen.findByRole('heading', { name: /trilha da liquidação/i })).toBeInTheDocument();
    // GET /execucao/liquidacoes/{id}/trilha real — evento de registro + ator mascarado.
    expect(await screen.findByText(/liquidação registrada/i)).toBeInTheDocument();
    expect(screen.getByText(/ator: \*\*\*\.456\.\*\*\*-\*\*/i)).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: /voltar para a fila de aprovação/i }));
    expect(await screen.findByRole('heading', { name: /fila de aprovação/i })).toBeInTheDocument();
  });

  it('rotas de registro de liquidação e pagamento estão montadas (esqueleto, sem formulário ainda)', async () => {
    const user = userEvent.setup();
    renderApp('/execucao/liquidacoes');

    expect(await screen.findByRole('heading', { name: /entrar/i })).toBeInTheDocument();
    await user.type(screen.getByLabelText('CPF'), '12345678900');
    await user.click(screen.getByRole('button', { name: /entrar/i }));

    expect(await screen.findByRole('heading', { name: /registrar liquidação/i })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: /registrar pagamento/i }));
    expect(await screen.findByRole('heading', { name: /registrar pagamento/i })).toBeInTheDocument();
  });
});
