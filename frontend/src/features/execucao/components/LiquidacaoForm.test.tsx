/**
 * Testa o driving adapter (RHF) isolado, com RTL — mesmo padrão de EmpenhoForm.test.tsx
 * (RAZ-202). Cobre: validação bloqueia submit sem tocar a API, fluxo feliz registra via o
 * caso de uso real (MSW) e reseta o formulário (inclusive o field array de documentos de
 * suporte de volta a 1 linha em branco), e erro do servidor aparece sem derrubar a tela.
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { useEffect, type ReactNode } from 'react';
import { AuthProvider, useAuth } from '../../../shared/auth/AuthContext';
import { server } from '../../../shared/api/mocks/server';
import { LiquidacaoForm } from './LiquidacaoForm';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const ENTE_ID = '11111111-1111-4111-8111-000000000009';
const EMPENHO_ID = '11111111-1111-4111-8111-000000000010';

function ComSessao({ children }: { children: ReactNode }) {
  const { sessao, entrar } = useAuth();
  useEffect(() => {
    if (!sessao) entrar({ cpfDigits: '12345678900', enteId: ENTE_ID, enteNome: 'Ente Teste' });
  }, [sessao, entrar]);
  if (!sessao) return null;
  return <>{children}</>;
}

function seedEmpenhoDisponivel() {
  server.use(
    http.get('/api/v1/entes/:enteId/execucao/empenhos', () =>
      HttpResponse.json({
        itens: [
          {
            id: EMPENHO_ID,
            numeroSequencial: 1,
            exercicio: new Date().getFullYear(),
            tipo: 'ordinario',
            credorId: '11111111-1111-4111-8111-000000000002',
            valor: '5000.00',
            dataFato: '2026-01-10',
            historico: 'Compra de material de expediente',
            status: 'registrado',
          },
        ],
        proximoCursor: null,
      }),
    ),
  );
}

function renderForm() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ComSessao>
          <LiquidacaoForm />
        </ComSessao>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

async function preencherCamposValidos(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByLabelText(/^empenho$/i));
  await user.click(await screen.findByRole('option', { name: /material de expediente/i }));
  await user.type(screen.getByLabelText(/data de competência/i), '2026-01-20');
  await user.type(screen.getByLabelText(/^valor/i), '1500.50');
  await user.type(screen.getByLabelText(/histórico/i), 'Liquidação de material de expediente');
  await user.type(screen.getByLabelText(/tipo do documento/i), 'nota_fiscal');
  await user.type(screen.getByLabelText(/número do documento/i), 'NF-123');
  await user.type(screen.getByLabelText(/data de emissão/i), '2026-01-18');
}

describe('LiquidacaoForm', () => {
  it('bloqueia o submit e mostra erros derivados do domínio quando os campos estão vazios', async () => {
    seedEmpenhoDisponivel();
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('form', { name: /registrar liquidação/i });
    await user.click(screen.getByRole('button', { name: /^registrar liquidação$/i }));

    expect(await screen.findByText('Selecione um empenho.')).toBeInTheDocument();
    expect(screen.getByText('Informe a data de competência.')).toBeInTheDocument();
    expect(screen.getByText('Informe um valor decimal válido (ex.: 1000.00).')).toBeInTheDocument();
    expect(screen.getByText('Informe o histórico.')).toBeInTheDocument();
    expect(screen.getByText('Informe o tipo do documento.')).toBeInTheDocument();
    expect(screen.getByText('Informe o número do documento.')).toBeInTheDocument();
    expect(screen.getByText('Informe a data de emissão.')).toBeInTheDocument();
    expect(screen.queryByText('Liquidação registrada.')).not.toBeInTheDocument();
  });

  it('registra a liquidação via o caso de uso real (MSW) e reseta o formulário', async () => {
    seedEmpenhoDisponivel();
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('form', { name: /registrar liquidação/i });
    await preencherCamposValidos(user);
    await user.click(screen.getByRole('button', { name: /^registrar liquidação$/i }));

    await waitFor(() => expect(screen.getByText('Liquidação registrada.')).toBeInTheDocument());
    expect(screen.getByLabelText(/histórico/i)).toHaveValue('');
    expect(screen.getByLabelText(/tipo do documento/i)).toHaveValue('');
    expect(screen.queryAllByLabelText(/tipo do documento/i)).toHaveLength(1);
  });

  it('adiciona e remove linhas de documento de suporte, nunca ficando abaixo de 1', async () => {
    seedEmpenhoDisponivel();
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('form', { name: /registrar liquidação/i });
    expect(screen.getAllByLabelText(/tipo do documento/i)).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: /adicionar documento de suporte/i }));
    expect(screen.getAllByLabelText(/tipo do documento/i)).toHaveLength(2);

    const removerBotoes = screen.getAllByRole('button', { name: /remover documento/i });
    expect(removerBotoes[0]).toBeEnabled();
    await user.click(removerBotoes[1]);
    expect(screen.getAllByLabelText(/tipo do documento/i)).toHaveLength(1);
    expect(screen.getByRole('button', { name: /remover documento 1/i })).toBeDisabled();
  });

  it('mostra o erro do servidor sem derrubar a tela', async () => {
    seedEmpenhoDisponivel();
    server.use(
      http.post('/api/v1/entes/:enteId/execucao/liquidacoes', () =>
        HttpResponse.json({ codigo: 'saldo_insuficiente', mensagem: 'Saldo insuficiente no empenho.', detalhes: {} }, { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('form', { name: /registrar liquidação/i });
    await preencherCamposValidos(user);
    await user.click(screen.getByRole('button', { name: /^registrar liquidação$/i }));

    expect(await screen.findByText(/Não foi possível registrar a liquidação: Saldo insuficiente no empenho\./)).toBeInTheDocument();
  });
});
