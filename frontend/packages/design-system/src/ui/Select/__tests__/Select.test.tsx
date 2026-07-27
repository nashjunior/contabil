/**
 * Prova de uso real do compound component (não unidades isoladas de cada subcomponente):
 * single, múltiplo (chips + remoção), assíncrono via useAsyncOptions, teclado/ARIA.
 * Debounce/cancelamento (AbortController) já têm cobertura de precisão em
 * useAsyncOptions.test.ts — aqui o foco é a integração com a UI.
 */
import '@testing-library/jest-dom/vitest';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Select, type SelectOption } from '../Select';
import { useAsyncOptions, type FetchOptions } from '../useAsyncOptions';

const CONTAS: SelectOption[] = [
  { value: '1', label: 'Caixa' },
  { value: '2', label: 'Bancos' },
  { value: '3', label: 'Aplicações financeiras' },
];

function SingleHarness({ options }: { options: SelectOption[] }) {
  const [value, setValue] = useState<string | null>(null);
  return (
    <div>
      <Select value={value} onChange={setValue} options={options} placeholder="Selecione a conta">
        <Select.Trigger aria-label="Conta contábil" />
        <Select.Options />
      </Select>
      <p>Selecionado: {value ?? '(nenhum)'}</p>
    </div>
  );
}

function MultipleHarness({ options }: { options: SelectOption[] }) {
  const [value, setValue] = useState<string[]>([]);
  return (
    <div>
      <Select multiple value={value} onChange={setValue} options={options} placeholder="Selecione as contas">
        <Select.Trigger aria-label="Contas contábeis" />
        <Select.Options />
      </Select>
      <p>Selecionados: {value.join(', ') || '(nenhum)'}</p>
    </div>
  );
}

function AsyncHarness({ fetchOptions }: { fetchOptions: FetchOptions }) {
  const [value, setValue] = useState<string | null>(null);
  const { query, setQuery, options, status, hasMore, loadMore } = useAsyncOptions({
    fetchOptions,
    debounceMs: 0,
  });
  return (
    <Select
      value={value}
      onChange={setValue}
      options={options}
      query={query}
      onQueryChange={setQuery}
      status={status}
      hasMore={hasMore}
      onLoadMore={loadMore}
      placeholder="Busque um credor"
    >
      <Select.Trigger aria-label="Credor" />
      <Select.Options />
    </Select>
  );
}

describe('Select — modo single', () => {
  it('filtra digitando, seleciona uma opção e fecha a lista', async () => {
    const user = userEvent.setup();
    render(<SingleHarness options={CONTAS} />);

    const combobox = screen.getByRole('combobox', { name: 'Conta contábil' });
    await user.click(combobox);
    expect(screen.getByRole('listbox')).toBeInTheDocument();
    expect(screen.getAllByRole('option')).toHaveLength(3);

    await user.type(combobox, 'ban');
    expect(screen.getAllByRole('option')).toHaveLength(1);
    expect(screen.getByRole('option', { name: 'Bancos' })).toBeInTheDocument();

    await user.click(screen.getByRole('option', { name: 'Bancos' }));

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(combobox).toHaveValue('Bancos');
    expect(screen.getByText('Selecionado: 2')).toBeInTheDocument();
  });

  it('filtro ignora acento/caixa (busca normalizada)', async () => {
    const user = userEvent.setup();
    render(<SingleHarness options={CONTAS} />);

    const combobox = screen.getByRole('combobox', { name: 'Conta contábil' });
    await user.click(combobox);
    await user.type(combobox, 'APLICACOES');

    expect(screen.getByRole('option', { name: 'Aplicações financeiras' })).toBeInTheDocument();
    expect(screen.getAllByRole('option')).toHaveLength(1);
  });
});

describe('Select — modo múltiplo', () => {
  it('seleciona várias opções com chips e remove individualmente (botão e Backspace)', async () => {
    const user = userEvent.setup();
    render(<MultipleHarness options={CONTAS} />);

    const combobox = screen.getByRole('combobox', { name: 'Contas contábeis' });
    await user.click(combobox);
    await user.click(screen.getByRole('option', { name: 'Caixa' }));
    await user.click(screen.getByRole('option', { name: 'Bancos' }));

    expect(screen.getByText('Selecionados: 1, 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Remover Caixa' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Remover Bancos' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Remover Caixa' }));
    expect(screen.getByText('Selecionados: 2')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Remover Caixa' })).not.toBeInTheDocument();

    await user.type(combobox, '{Backspace}');
    expect(screen.getByText('Selecionados: (nenhum)')).toBeInTheDocument();
  });
});

describe('Select — teclado e ARIA (combobox)', () => {
  it('expõe role=combobox/listbox/option e navega com seta/Enter/Escape', async () => {
    const user = userEvent.setup();
    render(<SingleHarness options={CONTAS} />);

    const combobox = screen.getByRole('combobox', { name: 'Conta contábil' });
    expect(combobox).toHaveAttribute('aria-expanded', 'false');
    expect(combobox).not.toHaveAttribute('aria-activedescendant');

    await user.click(combobox);
    await user.keyboard('{Escape}');
    expect(combobox).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

    await user.keyboard('{ArrowDown}');
    expect(combobox).toHaveAttribute('aria-expanded', 'true');
    const options = screen.getAllByRole('option');
    expect(options).toHaveLength(3);
    expect(combobox).toHaveAttribute('aria-activedescendant', options[0].id);

    await user.keyboard('{ArrowDown}');
    expect(combobox).toHaveAttribute('aria-activedescendant', options[1].id);

    await user.keyboard('{Enter}');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(combobox).toHaveValue('Bancos');
    expect(screen.getByText('Selecionado: 2')).toBeInTheDocument();
  });
});

describe('Select — assíncrono (useAsyncOptions)', () => {
  const FORNECEDORES = [
    { value: '10', label: 'Fornecedor Alfa' },
    { value: '11', label: 'Fornecedor Beta' },
  ];

  function buscaComAtraso(atrasoMs: number): FetchOptions {
    return vi.fn().mockImplementation(async ({ query }: { query: string }) => {
      await new Promise((resolve) => setTimeout(resolve, atrasoMs));
      const termo = query.toLowerCase();
      return { options: FORNECEDORES.filter((o) => o.label.toLowerCase().includes(termo)) };
    });
  }

  it('mostra "Carregando…" e depois as opções vindas do fetch; digitar refaz a busca', async () => {
    const user = userEvent.setup();
    const fetchOptions = buscaComAtraso(30);
    render(<AsyncHarness fetchOptions={fetchOptions} />);

    const combobox = screen.getByRole('combobox', { name: 'Credor' });
    await user.click(combobox);

    await waitFor(() => expect(screen.getByText('Carregando…')).toBeInTheDocument());
    await waitFor(() => expect(screen.getByRole('option', { name: 'Fornecedor Alfa' })).toBeInTheDocument());
    expect(screen.getByRole('option', { name: 'Fornecedor Beta' })).toBeInTheDocument();

    await user.type(combobox, 'beta');
    await waitFor(() => {
      expect(screen.getByRole('option', { name: 'Fornecedor Beta' })).toBeInTheDocument();
      expect(screen.queryByRole('option', { name: 'Fornecedor Alfa' })).not.toBeInTheDocument();
    });
  });

  it('mostra estado de erro quando a busca falha', async () => {
    const fetchOptions = vi.fn().mockRejectedValue(new Error('Falha ao buscar credores.'));
    const user = userEvent.setup();
    render(<AsyncHarness fetchOptions={fetchOptions} />);

    const combobox = screen.getByRole('combobox', { name: 'Credor' });
    await user.click(combobox);

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar as opções.');
  });

  it('mostra "Nenhuma opção encontrada." quando a busca não retorna resultados', async () => {
    const fetchOptions = vi.fn().mockResolvedValue({ options: [] });
    const user = userEvent.setup();
    render(<AsyncHarness fetchOptions={fetchOptions} />);

    const combobox = screen.getByRole('combobox', { name: 'Credor' });
    await user.click(combobox);

    expect(await screen.findByText('Nenhuma opção encontrada.')).toBeInTheDocument();
  });
});
