import { useState } from 'react';
import type { Meta, StoryObj } from '@storybook/react-vite';
import { Select, type SelectOption } from './Select';
import { useAsyncOptions } from './useAsyncOptions';
import { FIGMA_DESIGN_MAP } from '../../figma-map';

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
  parameters: {
    design: { type: 'figma', url: FIGMA_DESIGN_MAP['Select.Multiple'].url },
  },
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
  parameters: {
    design: { type: 'figma', url: FIGMA_DESIGN_MAP['Select.AsyncPanel'].url },
  },
};

/**
 * Cenário do "Picker de Conta PCASP" (RAZ-142/RAZ-194): a API devolve o catálogo como lista
 * PLANA ordenada por código (sem endpoint "filhos de X"), então a hierarquia é derivada
 * client-side pelo nº de segmentos do código (`depth`) e só a conta "escriturável" (Analítica)
 * é selecionável — a "Sintética" aparece para dar contexto mas fica `disabled`. Prova que este
 * mesmo `Select` (single + `depth` + `disabled` + conteúdo customizado por opção via
 * `Select.Options` render prop) cobre o cenário, sem precisar de um componente à parte.
 */
type ContaPcasp = SelectOption & {
  depth: number;
  natureza: string;
  escrituravel: boolean;
};

const CONTAS_PCASP: ContaPcasp[] = [
  { value: '1.1', label: 'Ativo circulante', depth: 0, natureza: 'D · Patrimonial · agregadora — não lança', escrituravel: false, disabled: true },
  { value: '1.1.1.01.01', label: 'Caixa e equivalentes de caixa', depth: 1, natureza: 'D · Patrimonial', escrituravel: true },
  { value: '1.1.1.01.02', label: 'Bancos conta movimento', depth: 1, natureza: 'D · Patrimonial', escrituravel: true },
  { value: '1.2', label: 'Ativo não circulante', depth: 0, natureza: 'D · Patrimonial · agregadora — não lança', escrituravel: false, disabled: true },
  { value: '1.2.1.01', label: 'Imóveis', depth: 1, natureza: 'D · Patrimonial', escrituravel: true },
];

function TagEscrituravel({ escrituravel }: { escrituravel: boolean }) {
  return (
    <span
      style={{
        flexShrink: 0,
        padding: 'var(--spacing-2xs) var(--spacing-sm)',
        borderRadius: 'var(--radius-sm)',
        fontSize: 'var(--typography-size-sm)',
        background: escrituravel ? 'var(--color-state-success-bg)' : 'var(--color-bg-inset)',
        color: escrituravel ? 'var(--color-state-success-fg)' : 'var(--color-text-secondary)',
      }}
    >
      {escrituravel ? 'Analítica' : 'Sintética'}
    </span>
  );
}

function PickerContaPcaspDemo() {
  const [contaId, setContaId] = useState<string | null>(null);
  return (
    <Select value={contaId} onChange={setContaId} options={CONTAS_PCASP} placeholder="Conta PCASP (código ou descrição)">
      <Select.Trigger aria-label="Conta PCASP" />
      <Select.Options>
        {(option, index) => (
          <Select.Option key={option.value} option={option} index={index}>
            <span style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-sm)', width: '100%' }}>
              <span style={{ flex: '1 1 auto', minWidth: 0 }}>
                <span style={{ display: 'block' }}>
                  {option.value} {option.label}
                </span>
                <span style={{ display: 'block', fontSize: 'var(--typography-size-sm)', color: 'var(--color-text-secondary)' }}>
                  {(option as ContaPcasp).natureza}
                </span>
              </span>
              <TagEscrituravel escrituravel={(option as ContaPcasp).escrituravel} />
            </span>
          </Select.Option>
        )}
      </Select.Options>
    </Select>
  );
}

export const PickerContaPcasp: Story = {
  render: () => <PickerContaPcaspDemo />,
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
