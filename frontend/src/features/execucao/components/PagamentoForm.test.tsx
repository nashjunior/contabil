/**
 * Testa o driving adapter (RHF) isolado, com RTL — mesmo padrão de EmpenhoForm.test.tsx/
 * LiquidacaoForm.test.tsx (RAZ-202/RAZ-230). Cobre: validação bloqueia submit, beneficiário
 * condicionalmente obrigatório (some para folha consolidada), fluxo feliz via MSW e reset, e
 * erro do servidor aparece sem derrubar a tela. O item aprovado do mock
 * (`FILA_APROVACAO_BASE`, `shared/api/mocks/handlers.ts`) já cobre o picker sem precisar de
 * override de handler.
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { useEffect, type ReactNode } from 'react';
import { AuthProvider, useAuth } from '../../../shared/auth/AuthContext';
import { server } from '../../../shared/api/mocks/server';
import { PagamentoForm } from './PagamentoForm';

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
          <PagamentoForm />
        </ComSessao>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

async function selecionarLiquidacaoAprovada(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByLabelText(/^liquidação aprovada$/i));
  await user.click(await screen.findByRole('option', { name: /820,75/ }));
}

async function preencherCamposValidos(user: ReturnType<typeof userEvent.setup>) {
  await selecionarLiquidacaoAprovada(user);
  await user.selectOptions(screen.getByLabelText(/^natureza/i), 'orcamentario');
  await user.type(screen.getByLabelText(/data de competência/i), '2026-01-25');
  await user.type(screen.getByLabelText(/^valor/i), '820.75');
  await user.type(screen.getByLabelText(/nome do beneficiário/i), 'Fornecedor Exemplo Ltda');
  await user.type(screen.getByLabelText(/cpf\/cnpj do beneficiário/i), '12345678000199');
  await user.type(screen.getByLabelText(/histórico/i), 'Pagamento de material de expediente');
}

describe('PagamentoForm', () => {
  it('bloqueia o submit e mostra erros derivados do domínio quando os campos estão vazios', async () => {
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('form', { name: /registrar pagamento/i });
    await user.click(screen.getByRole('button', { name: /^registrar pagamento$/i }));

    expect(await screen.findByText('Selecione uma liquidação aprovada.')).toBeInTheDocument();
    expect(screen.getByText('Selecione a natureza do pagamento.')).toBeInTheDocument();
    expect(screen.getByText('Informe a data de competência.')).toBeInTheDocument();
    expect(screen.getByText('Informe um valor decimal válido (ex.: 1000.00).')).toBeInTheDocument();
    expect(screen.getByText('Informe o histórico.')).toBeInTheDocument();
    expect(screen.queryByText('Pagamento registrado.')).not.toBeInTheDocument();
  });

  it('exige nome e CPF/CNPJ do beneficiário para natureza orçamentária', async () => {
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('form', { name: /registrar pagamento/i });
    await user.selectOptions(screen.getByLabelText(/^natureza/i), 'orcamentario');
    await user.click(screen.getByRole('button', { name: /^registrar pagamento$/i }));

    expect(await screen.findByText('Informe o nome do beneficiário.')).toBeInTheDocument();
    expect(screen.getByText('Informe o CPF/CNPJ do beneficiário.')).toBeInTheDocument();
  });

  it('não exige beneficiário para folha consolidada', async () => {
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('form', { name: /registrar pagamento/i });
    await selecionarLiquidacaoAprovada(user);
    await user.selectOptions(screen.getByLabelText(/^natureza/i), 'folha_consolidada');
    await user.type(screen.getByLabelText(/data de competência/i), '2026-01-25');
    await user.type(screen.getByLabelText(/^valor/i), '820.75');
    await user.type(screen.getByLabelText(/histórico/i), 'Folha consolidada de janeiro');
    await user.click(screen.getByRole('button', { name: /^registrar pagamento$/i }));

    expect(screen.queryByText('Informe o nome do beneficiário.')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Pagamento registrado.')).toBeInTheDocument());
  });

  it('registra o pagamento via o caso de uso real (MSW) e reseta o formulário', async () => {
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('form', { name: /registrar pagamento/i });
    await preencherCamposValidos(user);
    await user.click(screen.getByRole('button', { name: /^registrar pagamento$/i }));

    await waitFor(() => expect(screen.getByText('Pagamento registrado.')).toBeInTheDocument());
    expect(screen.getByLabelText(/histórico/i)).toHaveValue('');
    expect(screen.getByLabelText(/nome do beneficiário/i)).toHaveValue('');
  });

  it('mostra o erro do servidor sem derrubar a tela', async () => {
    server.use(
      http.post('/api/v1/entes/:enteId/execucao/pagamentos', () =>
        HttpResponse.json({ codigo: 'saldo_insuficiente', mensagem: 'Saldo insuficiente na liquidação.', detalhes: {} }, { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('form', { name: /registrar pagamento/i });
    await preencherCamposValidos(user);
    await user.click(screen.getByRole('button', { name: /^registrar pagamento$/i }));

    expect(await screen.findByText(/Não foi possível registrar o pagamento: Saldo insuficiente na liquidação\./)).toBeInTheDocument();
  });
});
