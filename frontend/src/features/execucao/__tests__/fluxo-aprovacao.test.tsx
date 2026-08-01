/**
 * Prova fim-a-fim das rotas de execução (RAZ-221/ADR-0052 + RAZ-230): login dev -> fila de
 * aprovação (GET /execucao/liquidacoes real) lista o item pendente -> "Ver trilha" navega para
 * a trilha dedicada (GET /execucao/liquidacoes/{id}/trilha real) -> volta para a fila; e
 * navegação entre as rotas de registro de liquidação/pagamento, já com formulário real (RAZ-230)
 * montado em cada uma. A decisão do gate (aprovar/devolver, botão "Decidir" -> `GateAprovacaoModal`,
 * ADR-0055) tem cobertura própria e mais profunda em `GateAprovacaoModal.test.tsx` — aqui só a
 * prova de que o botão está acessível a partir da fila real.
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

  it('o botão "Decidir" do item pendente abre o modal de decisão do gate (RAZ-229/ADR-0055)', async () => {
    const user = userEvent.setup();
    renderApp('/execucao/aprovacoes');

    expect(await screen.findByRole('heading', { name: /entrar/i })).toBeInTheDocument();
    await user.type(screen.getByLabelText('CPF'), '12345678900');
    await user.click(screen.getByRole('button', { name: /entrar/i }));

    expect(await screen.findByRole('heading', { name: /fila de aprovação/i })).toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: /^decidir$/i }));

    expect(await screen.findByRole('dialog', { name: /decidir liquidação/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^aprovar$/i })).toBeInTheDocument();
  });

  it('rotas de registro de liquidação e pagamento estão montadas, cada uma com formulário real (RAZ-230)', async () => {
    const user = userEvent.setup();
    renderApp('/execucao/liquidacoes');

    expect(await screen.findByRole('heading', { name: /entrar/i })).toBeInTheDocument();
    await user.type(screen.getByLabelText('CPF'), '12345678900');
    await user.click(screen.getByRole('button', { name: /entrar/i }));

    expect(await screen.findByRole('heading', { name: /registrar liquidação/i })).toBeInTheDocument();
    expect(await screen.findByRole('form', { name: /registrar liquidação/i })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: /registrar pagamento/i }));
    expect(await screen.findByRole('heading', { name: /registrar pagamento/i })).toBeInTheDocument();
    expect(await screen.findByRole('form', { name: /registrar pagamento/i })).toBeInTheDocument();
  });
});
