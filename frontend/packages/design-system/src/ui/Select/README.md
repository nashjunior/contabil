# Select — combobox reutilizável (RAZ-122)

Compound component (ADR-0032): `Select` + `Select.Trigger` + `Select.Options` +
`Select.Option`, composição via Context — nenhum subcomponente recebe estado
por prop drilling. Segue o padrão ARIA combobox (`role="combobox"`,
`aria-expanded`, `aria-activedescendant`, navegação por teclado com foco
sempre no input). Reusa o Select Field e a Tag (chips) já desenhados na Simple
Design System (RAZ-145); este componente acrescenta o painel de estado
assíncrono (Carregando/Vazio/Erro) e a casca de múltipla seleção.

A lógica de carga assíncrona (debounce, cancelamento via `AbortController`,
estados loading/vazio/erro, cache/paginação) **não vive no componente** — fica
isolada no hook `useAsyncOptions` (mesma pasta). O `Select` só recebe dados já
prontos (`options`, `status`, etc.); ele não sabe o que é um `fetch`.

Todo o visual usa tokens (`var(--color-*)`, `var(--spacing-*)`,
`var(--radius-*)`, `var(--typography-*)`) do `@siafic/design-system`
(`src/tokens/theme.css`) — nenhuma cor/spacing hardcoded.

## Uso síncrono (lista local)

```tsx
import { useState } from 'react';
import { Select, type SelectOption } from '@siafic/design-system';

const CONTAS: SelectOption[] = [
  { value: '1.1.1.01', label: 'Caixa' },
  { value: '1.1.1.02', label: 'Bancos conta movimento' },
  { value: '1.1.2.01', label: 'Aplicações financeiras' },
];

function SelecionarConta() {
  const [conta, setConta] = useState<string | null>(null);

  return (
    <Select value={conta} onChange={setConta} options={CONTAS} placeholder="Selecione a conta">
      <Select.Trigger aria-label="Conta contábil" />
      <Select.Options />
    </Select>
  );
}
```

Digitar no campo filtra `CONTAS` localmente (por `label`, normalizado —
ignora acento/caixa). Para modo múltiplo (chips com remoção individual —
clique no × ou Backspace com o campo vazio):

```tsx
const [contas, setContas] = useState<string[]>([]);

<Select multiple value={contas} onChange={setContas} options={CONTAS} placeholder="Selecione as contas">
  <Select.Trigger aria-label="Contas contábeis" />
  <Select.Options />
</Select>;
```

### PII mascarada (modo síncrono)

Quando o `label` exibido precisa estar mascarado (ex.: CPF), use
`searchValue` para manter o filtro funcional sobre o texto não mascarado sem
expor PII na tela:

```tsx
import { maskCpf } from '../../../shared/lib/cpf';

const CREDORES: SelectOption[] = pessoas.map((p) => ({
  value: p.id,
  label: `${p.nome} — ${maskCpf(p.cpf)}`,
  searchValue: p.nome, // filtro por nome; nunca pelo CPF cru
}));
```

## Uso assíncrono (`useAsyncOptions`)

```tsx
import { useState } from 'react';
import { Select, useAsyncOptions } from '@siafic/design-system';
import { maskCpf } from '../../../shared/lib/cpf';

function SelecionarCredor() {
  const [credorId, setCredorId] = useState<string | null>(null);

  const { query, setQuery, options, status, hasMore, loadMore } = useAsyncOptions({
    debounceMs: 300, // espera o usuário parar de digitar antes de buscar
    minChars: 2, // não busca com menos de 2 caracteres
    fetchOptions: async ({ query, page, signal }) => {
      const res = await fetch(`/api/credores?busca=${encodeURIComponent(query)}&pagina=${page}`, { signal });
      if (!res.ok) throw new Error('Falha ao buscar credores.');
      const data: { id: string; nome: string; cpf: string; temMaisPaginas: boolean }[] = await res.json();
      return {
        options: data.map((c) => ({ value: c.id, label: `${c.nome} — ${maskCpf(c.cpf)}`, searchValue: c.nome })),
        hasMore: data.temMaisPaginas,
      };
    },
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
      placeholder="Busque um credor por nome"
    >
      <Select.Trigger aria-label="Credor" />
      <Select.Options />
    </Select>
  );
}
```

Passar `onQueryChange` é o que diz ao `Select` "não filtre `options`
localmente, elas já vêm filtradas do servidor" — o `useAsyncOptions` cuida de:

- **Debounce**: só chama `fetchOptions` `debounceMs` depois da última tecla.
- **Cancelamento**: cada nova busca aborta (`AbortController`) a anterior
  ainda em voo — resposta obsoleta nunca sobrescreve resultado mais novo.
- **Estados**: `status` é `'idle' | 'loading' | 'success' | 'empty' | 'error'`
  — `Select.Options` já renderiza um painel com ícone + "Carregando
  opções…"/"Nenhuma opção encontrada."/mensagem de erro para cada um (fiel ao
  Figma RAZ-145, RAZ-194).
- **Cache + paginação**: respostas ficam em cache por `query`+página (evita
  refetch ao repetir uma busca já feita); `hasMore`/`loadMore` cobrem
  paginação quando `fetchOptions` retorna `{ options, hasMore: true }`.

## Renderização customizada por opção

`Select.Options` aceita uma render prop quando o item precisa de mais que
`label` simples (ex.: subtítulo, ícone) — ainda assim sem prop drilling, pois
`Select.Option` lê seleção/destaque do Context:

```tsx
<Select.Options>
  {(option, index) => (
    <Select.Option key={option.value} option={option} index={index}>
      <strong>{option.label}</strong>
    </Select.Option>
  )}
</Select.Options>
```
