/**
 * RAZ-242 — o form de dev deixou de fabricar o bearer opaco `dev.<cpf>.<ente>` e passou a
 * chamar `POST /sessao/dev-idp/token` (RAZ-228). Cobre: o bearer gravado na sessão vem da
 * resposta do endpoint (não mais fabricado), o botão desabilita/mostra "Entrando…" durante o
 * round-trip, e os dois erros documentados do contrato (mesmo que hoje inalcançáveis pela
 * validação client-side — defesa em profundidade) mostram mensagem amigável sem derrubar a
 * tela nem navegar.
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider, useAuth } from '../AuthContext';
import { LoginPage } from '../LoginPage';
import { server } from '../../api/mocks/server';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function SessaoDebug() {
  const { sessao } = useAuth();
  return <p data-testid="bearer-atual">{sessao?.bearerToken ?? ''}</p>;
}

function renderLoginPage() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/entrar']}>
        <LoginPage />
        <SessaoDebug />
      </MemoryRouter>
    </AuthProvider>,
  );
}

async function preencherEEnviar(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('heading', { name: /entrar/i });
  await user.type(screen.getByLabelText('CPF'), '12345678900');
  await user.click(screen.getByRole('button', { name: /entrar/i }));
}

describe('LoginPage — modo desenvolvimento via POST /sessao/dev-idp/token', () => {
  it('grava o bearer devolvido pelo endpoint (não mais a string opaca dev.<cpf>.<ente>)', async () => {
    const user = userEvent.setup();
    renderLoginPage();

    await preencherEEnviar(user);

    await waitFor(() => expect(screen.getByTestId('bearer-atual')).toHaveTextContent(/^dev-idp-mock\./));
    expect(screen.getByTestId('bearer-atual').textContent).not.toMatch(/^dev\.\d{11}\./);
  });

  it('desabilita o botão e mostra "Entrando…" durante o round-trip', async () => {
    server.use(
      http.post('/sessao/dev-idp/token', async () => {
        await new Promise((resolve) => setTimeout(resolve, 20));
        return HttpResponse.json({ bearerToken: 'dev-idp-mock.atrasado' });
      }),
    );
    const user = userEvent.setup();
    renderLoginPage();

    await screen.findByRole('heading', { name: /entrar/i });
    await user.type(screen.getByLabelText('CPF'), '12345678900');
    await user.click(screen.getByRole('button', { name: /^entrar$/i }));

    expect(await screen.findByRole('button', { name: /entrando/i })).toBeDisabled();
  });

  it('erro cpf_invalido do endpoint mostra mensagem amigável sem navegar', async () => {
    server.use(http.post('/sessao/dev-idp/token', () => HttpResponse.json({ erro: 'cpf_invalido' }, { status: 400 })));
    const user = userEvent.setup();
    renderLoginPage();

    await preencherEEnviar(user);

    expect(await screen.findByText('Informe um CPF com 11 dígitos.')).toBeInTheDocument();
    expect(screen.getByTestId('bearer-atual')).toHaveTextContent('');
    expect(screen.getByRole('button', { name: /^entrar$/i })).toBeEnabled();
  });

  it('erro ente_id_invalido do endpoint mostra mensagem amigável', async () => {
    server.use(http.post('/sessao/dev-idp/token', () => HttpResponse.json({ erro: 'ente_id_invalido' }, { status: 400 })));
    const user = userEvent.setup();
    renderLoginPage();

    await preencherEEnviar(user);

    expect(await screen.findByText('Selecione um ente válido.')).toBeInTheDocument();
  });

  it('falha de rede/erro não mapeado mostra mensagem genérica, não a mensagem crua', async () => {
    server.use(http.post('/sessao/dev-idp/token', () => HttpResponse.error()));
    const user = userEvent.setup();
    renderLoginPage();

    await preencherEEnviar(user);

    expect(await screen.findByText('Não foi possível entrar. Verifique sua conexão e tente novamente.')).toBeInTheDocument();
  });
});
