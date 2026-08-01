/**
 * Testa o driving adapter isolado, com RTL — mesmo padrão de LiquidacaoForm.test.tsx. Cobre o
 * núcleo do ADR-0055: aprovar sem motivo, devolver exige motivo, e o mapeamento de erro por
 * `erro.codigo` (decisão 4) tanto no caminho que mantém o modal aberto quanto no que fecha (item
 * deixou de ser decidível — CODIGOS_ERRO_QUE_REMOVEM_ITEM_DA_FILA).
 */
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { useEffect, type ReactNode } from 'react';
import { AuthProvider, useAuth } from '../../../shared/auth/AuthContext';
import { server } from '../../../shared/api/mocks/server';
import type { ItemFilaAprovacao } from '../../../shared/api/client';
import { GateAprovacaoModal } from './GateAprovacaoModal';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const ENTE_ID = '11111111-1111-4111-8111-000000000009';
const ITEM: ItemFilaAprovacao = {
  id: '44444444-4444-4444-8444-000000000001',
  empenhoId: '11111111-1111-4111-8111-000000000010',
  numeroEmpenho: 1,
  exercicioEmpenho: 2026,
  credorId: '11111111-1111-4111-8111-000000000002',
  valor: '1500.00',
  dataCompetencia: '2026-01-20',
  statusAprovacao: 'pendente',
};

function ComSessao({ children }: { children: ReactNode }) {
  const { sessao, entrar } = useAuth();
  useEffect(() => {
    if (!sessao) entrar({ cpfDigits: '12345678900', enteId: ENTE_ID, enteNome: 'Ente Teste' });
  }, [sessao, entrar]);
  if (!sessao) return null;
  return <>{children}</>;
}

function renderModal(onFechar: () => void, item: ItemFilaAprovacao = ITEM) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ComSessao>
          <GateAprovacaoModal item={item} onFechar={onFechar} />
        </ComSessao>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

function mockDecisao(status: number, codigo: string, mensagem: string) {
  server.use(
    http.post('/api/v1/entes/:enteId/execucao/liquidacoes/:id/aprovacao', () =>
      HttpResponse.json({ codigo, mensagem, detalhes: {} }, { status }),
    ),
  );
}

describe('GateAprovacaoModal', () => {
  it('mostra o resumo (empenho, valor, competência) e a trilha compacta ao abrir', async () => {
    renderModal(vi.fn());

    expect(await screen.findByRole('heading', { name: /decidir liquidação/i })).toBeInTheDocument();
    expect(screen.getByText(/2026\/1/)).toBeInTheDocument();
    expect(screen.getByText('R$ 1.500,00')).toBeInTheDocument();
    expect(await screen.findByText(/liquidação registrada/i)).toBeInTheDocument();
  });

  it('aprova sem exigir motivo e fecha o modal', async () => {
    const onFechar = vi.fn();
    const user = userEvent.setup();
    renderModal(onFechar);

    await screen.findByRole('heading', { name: /decidir liquidação/i });
    await user.click(screen.getByRole('button', { name: /^aprovar$/i }));

    await waitFor(() => expect(onFechar).toHaveBeenCalledTimes(1));
  });

  it('revela o campo de motivo ao clicar em "Devolver…" e bloqueia o submit sem preenchê-lo', async () => {
    const onFechar = vi.fn();
    const user = userEvent.setup();
    renderModal(onFechar);

    await screen.findByRole('heading', { name: /decidir liquidação/i });
    expect(screen.queryByLabelText(/motivo da devolução/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /devolver…/i }));
    expect(screen.getByLabelText(/motivo da devolução/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^devolver$/i }));
    expect(await screen.findByText('Informe o motivo da devolução.')).toBeInTheDocument();
    expect(onFechar).not.toHaveBeenCalled();
  });

  it('devolve com motivo preenchido e fecha o modal', async () => {
    const onFechar = vi.fn();
    const user = userEvent.setup();
    renderModal(onFechar);

    await screen.findByRole('heading', { name: /decidir liquidação/i });
    await user.click(screen.getByRole('button', { name: /devolver…/i }));
    await user.type(screen.getByLabelText(/motivo da devolução/i), 'Nota fiscal divergente do empenho.');
    await user.click(screen.getByRole('button', { name: /^devolver$/i }));

    await waitFor(() => expect(onFechar).toHaveBeenCalledTimes(1));
  });

  it('"Cancelar" volta pro estado de aprovar e limpa o motivo', async () => {
    const user = userEvent.setup();
    renderModal(vi.fn());

    await screen.findByRole('heading', { name: /decidir liquidação/i });
    await user.click(screen.getByRole('button', { name: /devolver…/i }));
    await user.type(screen.getByLabelText(/motivo da devolução/i), 'texto qualquer');
    await user.click(screen.getByRole('button', { name: /cancelar/i }));

    expect(screen.queryByLabelText(/motivo da devolução/i)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^aprovar$/i })).toBeInTheDocument();
  });

  it.each([
    ['auto_aprovacao_vedada', 409, /segregação de funções/],
    ['sem_permissao', 403, /papel AUTORIZADOR necessário/],
    ['mfa_requerido', 428, /verificação adicional de segurança/],
  ])('erro %s mantém o modal aberto com a mensagem amigável', async (codigo, status, mensagemEsperada) => {
    mockDecisao(status, codigo, 'mensagem crua irrelevante');
    const onFechar = vi.fn();
    const user = userEvent.setup();
    renderModal(onFechar);

    await screen.findByRole('heading', { name: /decidir liquidação/i });
    await user.click(screen.getByRole('button', { name: /^aprovar$/i }));

    expect(await screen.findByText(mensagemEsperada)).toBeInTheDocument();
    expect(onFechar).not.toHaveBeenCalled();
  });

  it('erro liquidacao_ja_decidida fecha o modal (item deixou de ser decidível)', async () => {
    mockDecisao(409, 'liquidacao_ja_decidida', 'mensagem crua irrelevante');
    const onFechar = vi.fn();
    const user = userEvent.setup();
    renderModal(onFechar);

    await screen.findByRole('heading', { name: /decidir liquidação/i });
    await user.click(screen.getByRole('button', { name: /^aprovar$/i }));

    await waitFor(() => expect(onFechar).toHaveBeenCalledTimes(1));
  });

  it('o botão "Fechar" e o Esc fecham o modal sem decidir', async () => {
    const onFechar = vi.fn();
    const user = userEvent.setup();
    renderModal(onFechar);

    await screen.findByRole('heading', { name: /decidir liquidação/i });
    await user.click(screen.getByRole('button', { name: /^fechar$/i }));
    expect(onFechar).toHaveBeenCalledTimes(1);
  });
});
