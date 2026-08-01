/**
 * Testa o driving adapter (RHF) isolado, com RTL — complementa os testes framework-free
 * de domínio/schema/use case (RAZ-202). Cobre: validação bloqueia submit sem tocar a API,
 * fluxo feliz registra via o caso de uso real (MSW) e reseta o formulário, e erro do
 * servidor aparece sem derrubar a tela.
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { useEffect, type ReactNode } from 'react';
import { AuthProvider, useAuth } from '../../../shared/auth/AuthContext';
import { server } from '../../../shared/api/mocks/server';
import { EmpenhoForm } from './EmpenhoForm';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const ENTE_ID = '11111111-1111-4111-8111-000000000009';

function ComSessao({ children }: { children: ReactNode }) {
  const { sessao, entrar } = useAuth();
  useEffect(() => {
    if (!sessao) entrar({ cpfDigits: '12345678900', enteId: ENTE_ID, enteNome: 'Ente Teste' });
  }, [sessao, entrar]);
  if (!sessao) return null;
  return <>{children}</>;
}

function renderForm() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ComSessao>
          <EmpenhoForm />
        </ComSessao>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

async function preencherCamposValidos(user: ReturnType<typeof userEvent.setup>) {
  // DotacaoPicker (RAZ-199): combo assíncrono, não texto livre — abre e escolhe pelo rótulo.
  await user.click(screen.getByLabelText(/dotação/i));
  await user.click(await screen.findByRole('option', { name: /04\.122\.0001\.2001\.3\.3\.90\.30/ }));
  await user.selectOptions(screen.getByLabelText(/^tipo/i), 'ordinario');
  await user.type(screen.getByLabelText(/id do credor/i), '11111111-1111-4111-8111-000000000002');
  await user.type(screen.getByLabelText(/id da unidade gestora/i), '11111111-1111-4111-8111-000000000003');
  await user.type(screen.getByLabelText(/^valor/i), '1000.50');
  await user.type(screen.getByLabelText(/data do fato/i), '2026-01-15');
  await user.clear(screen.getByLabelText(/exercício/i));
  await user.type(screen.getByLabelText(/exercício/i), '2026');
  await user.type(screen.getByLabelText(/classificação orçamentária/i), '3.3.90.30');
  await user.type(screen.getByLabelText(/fonte de recurso/i), '0100');
  await user.type(screen.getByLabelText(/histórico/i), 'Compra de material de expediente');
}

describe('EmpenhoForm', () => {
  it('bloqueia o submit e mostra erros derivados do domínio quando os campos estão vazios', async () => {
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('form', { name: /registrar empenho/i });
    await user.click(screen.getByRole('button', { name: /registrar empenho/i }));

    expect(await screen.findByText('Selecione uma dotação.')).toBeInTheDocument();
    expect(screen.getAllByText('Informe um UUID válido.')).not.toHaveLength(0);
    expect(screen.getByText('Selecione o tipo.')).toBeInTheDocument();
    expect(screen.getByText('Informe um valor decimal válido (ex.: 1000.00).')).toBeInTheDocument();
    expect(screen.queryByText('Empenho registrado.')).not.toBeInTheDocument();
  });

  it('registra o empenho via o caso de uso real (MSW) e reseta o formulário', async () => {
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('form', { name: /registrar empenho/i });
    await preencherCamposValidos(user);
    await user.click(screen.getByRole('button', { name: /registrar empenho/i }));

    await waitFor(() => expect(screen.getByText('Empenho registrado.')).toBeInTheDocument());
    expect(screen.getByLabelText(/dotação/i)).toHaveValue('');
    expect(screen.getByLabelText(/histórico/i)).toHaveValue('');
  });

  it('mostra o erro do servidor sem derrubar a tela', async () => {
    server.use(
      http.post('/api/v1/entes/:enteId/execucao/empenhos', () =>
        HttpResponse.json({ codigo: 'saldo_insuficiente', mensagem: 'Saldo insuficiente na dotação.', detalhes: {} }, { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('form', { name: /registrar empenho/i });
    await preencherCamposValidos(user);
    await user.click(screen.getByRole('button', { name: /registrar empenho/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível registrar o empenho: Saldo insuficiente na dotação.');
  });
});
