/**
 * RAZ-239 — "sessão expirada" distinta de "nunca logou", só client-side (sem contrato/backend,
 * ADR-0035). Prova a convenção fim-a-fim pelas rotas reais (`AppRoutes`), interceptada pelo MSW
 * no mesmo contrato do código real:
 *  - nunca logou: hidratação `GET /sessao/atual` devolve 401 → `RequireAuth` manda pra `/entrar`
 *    SEM motivo → formulário simples, nenhum alerta;
 *  - sessão cai no meio do uso: uma chamada AUTENTICADA (`GET /execucao/empenhos`, disparada por
 *    `ExecucaoPage`) devolve 401 depois de já ter havido sessão → `AuthContext` marca expiração,
 *    `RequireAuth` anexa o motivo e `LoginPage` mostra o Alert "Sua sessão expirou".
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthProvider } from '../AuthContext';
import { AppRoutes } from '../../../app/AppRoutes';
import { server } from '../../api/mocks/server';

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

const ALERTA_EXPIRADA = /sua sessão expirou/i;

describe('RAZ-239 — sinal de sessão expirada vs nunca-logado', () => {
  it('nunca logou: redireciona para /entrar sem o alerta de expiração', async () => {
    renderApp(); // /execucao sem cookie → hidratação 401 (normal) → /entrar

    expect(await screen.findByRole('heading', { name: /entrar/i })).toBeInTheDocument();
    expect(screen.queryByText(ALERTA_EXPIRADA)).not.toBeInTheDocument();
  });

  it('sessão cai no meio do uso: 401 numa chamada autenticada leva a /entrar com o alerta', async () => {
    const user = userEvent.setup();
    renderApp();

    // Chega ao form de login (nunca-logado) e faz login dev — a partir daqui HÁ sessão.
    expect(await screen.findByRole('heading', { name: /entrar/i })).toBeInTheDocument();
    expect(screen.queryByText(ALERTA_EXPIRADA)).not.toBeInTheDocument();

    // A próxima chamada autenticada da ExecucaoPage (GET /execucao/empenhos) volta 401 —
    // sessão expirou/foi revogada depois de já existir.
    server.use(
      http.get('/api/v1/entes/:enteId/execucao/empenhos', () =>
        HttpResponse.json({ codigo: 'nao_autenticado', mensagem: 'Sessão expirada.', detalhes: {} }, { status: 401 }),
      ),
    );

    await user.type(screen.getByLabelText('CPF'), '12345678900');
    await user.click(screen.getByRole('button', { name: /entrar/i }));

    // De volta ao /entrar, agora com o Alert Nível=Atenção (role="alert") em vez do form seco.
    const alerta = await screen.findByRole('alert');
    expect(alerta).toHaveTextContent(ALERTA_EXPIRADA);
    expect(alerta).toHaveTextContent(/sessão expirada/i);
  });
});
