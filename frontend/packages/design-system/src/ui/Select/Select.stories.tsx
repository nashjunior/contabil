import { useState } from 'react';
import type { Meta, StoryObj } from '@storybook/react-vite';
import { Select, type SelectOption } from './Select';
import { useAsyncOptions } from './useAsyncOptions';

const meta: Meta<typeof Select> = {
  title: 'Componentes/Select',
  component: Select,
  tags: ['autodocs'],
};
export default meta;

type Story = StoryObj<typeof Select>;

const CONTAS: SelectOption[] = [
  { value: '1.1.1.01', label: 'Caixa' },
  { value: '1.1.1.02', label: 'Bancos conta movimento' },
  { value: '1.1.2.01', label: 'Aplicações financeiras' },
  { value: '2.1.1.01', label: 'Fornecedores a pagar' },
];

function SincronoUnicoDemo() {
  const [conta, setConta] = useState<string | null>(null);
  return (
    <Select value={conta} onChange={setConta} options={CONTAS} placeholder="Selecione a conta">
      <Select.Trigger aria-label="Conta contábil" />
      <Select.Options />
    </Select>
  );
}

export const SincronoUnico: Story = {
  render: () => <SincronoUnicoDemo />,
};

function SincronoMultiploDemo() {
  const [contas, setContas] = useState<string[]>([CONTAS[0]!.value]);
  return (
    <Select multiple value={contas} onChange={setContas} options={CONTAS} placeholder="Selecione as contas">
      <Select.Trigger aria-label="Contas contábeis" />
      <Select.Options />
    </Select>
  );
}

export const SincronoMultiplo: Story = {
  render: () => <SincronoMultiploDemo />,
};

const TODOS_CREDORES: SelectOption[] = [
  { value: 'c1', label: 'Ana Paula Souza' },
  { value: 'c2', label: 'Bruno Ferreira Lima' },
  { value: 'c3', label: 'Carla Mendes' },
  { value: 'c4', label: 'Diego Santos' },
  { value: 'c5', label: 'Empresa XPTO Serviços Ltda' },
];

const PAGE_SIZE = 2;

function buscarCredoresSimulado({
  query,
  page,
  signal,
}: {
  query: string;
  page: number;
  signal: AbortSignal;
}): Promise<{ options: SelectOption[]; hasMore: boolean }> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      const encontrados = TODOS_CREDORES.filter((credor) =>
        credor.label.toLowerCase().includes(query.toLowerCase()),
      );
      const pagina = encontrados.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);
      resolve({ options: pagina, hasMore: page * PAGE_SIZE + PAGE_SIZE < encontrados.length });
    }, 400);
    signal.addEventListener('abort', () => {
      clearTimeout(timer);
      reject(new DOMException('Busca cancelada.', 'AbortError'));
    });
  });
}

function AssincronoDemo() {
  const [credorId, setCredorId] = useState<string | null>(null);
  const { query, setQuery, options, status, hasMore, loadMore } = useAsyncOptions({
    debounceMs: 300,
    fetchOptions: buscarCredoresSimulado,
  });

  return (
    <Select
      value={credorId}
      onChange={setCredorId}
      options={options}
      query={query}
      onQueryChange={setQuery}
      status={status}
      hasMore={hasMore}
      onLoadMore={loadMore}
      placeholder="Busque um credor por nome (simulado, 400ms)"
    >
      <Select.Trigger aria-label="Credor" />
      <Select.Options />
    </Select>
  );
}

export const Assincrono: Story = {
  render: () => <AssincronoDemo />,
};

function DesabilitadoDemo() {
  return (
    <Select value={CONTAS[0]!.value} onChange={() => {}} options={CONTAS} disabled placeholder="Selecione a conta">
      <Select.Trigger aria-label="Conta contábil" />
      <Select.Options />
    </Select>
  );
}

export const Desabilitado: Story = {
  render: () => <DesabilitadoDemo />,
};
