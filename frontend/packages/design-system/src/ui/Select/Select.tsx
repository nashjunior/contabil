/**
 * Compound component (ADR-0032): Trigger/Options/Option leem o estado via Context, nao
 * via prop repassada pelo pai — nenhum prop drilling em subcomponente aninhado. Uso (sync):
 *   <Select value={value} onChange={setValue} options={options} placeholder="Selecione">
 *     <Select.Trigger aria-label="Conta contábil" />
 *     <Select.Options />
 *   </Select>
 * Modo multiplo: prop `multiple` + `value`/`onChange` como array (chips com remoção individual
 * no Trigger). Carga assíncrona: NÃO é responsabilidade deste componente — use o hook
 * `useAsyncOptions` (mesma pasta) e passe `options`/`query`/`onQueryChange`/`status`/`hasMore`/
 * `onLoadMore`; ver README.md desta pasta para exemplos completos (sync e async).
 * Reusa Select Field/Tag da Simple Design System (RAZ-145); adiciona o painel de estado
 * assíncrono (Carregando/Vazio/Erro) e a casca de múltipla seleção.
 * Co-location: Context + subcomponentes no mesmo arquivo — o consumidor externo só importa
 * `Select`/`useAsyncOptions` (index.ts), nunca o Context bruto.
 */
import {
  createContext,
  useContext,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
  type RefObject,
} from 'react';

export type SelectOption = {
  value: string;
  label: string;
  /**
   * Texto usado para o filtro local (modo síncrono) quando `label` está mascarado por PII
   * (ex.: CPF). Se omitido, filtra por `label`. Ignorado no modo assíncrono/servidor (a busca
   * já chega filtrada de `fetchOptions`).
   */
  searchValue?: string;
  disabled?: boolean;
};

export type SelectStatus = 'idle' | 'loading' | 'success' | 'empty' | 'error';

type SelectContextValue = {
  multiple: boolean;
  disabled: boolean;
  open: boolean;
  setOpen: (open: boolean) => void;
  query: string;
  setQuery: (query: string) => void;
  visibleOptions: SelectOption[];
  status: SelectStatus;
  errorMessage?: string;
  isSelected: (value: string) => boolean;
  selectOption: (option: SelectOption) => void;
  removeValue: (value: string) => void;
  selectedOptions: SelectOption[];
  activeIndex: number;
  setActiveIndex: (index: number) => void;
  listboxId: string;
  inputId: string;
  getOptionId: (index: number) => string;
  inputRef: RefObject<HTMLInputElement | null>;
  placeholder?: string;
  hasMore: boolean;
  onLoadMore?: () => void;
};

const SelectContext = createContext<SelectContextValue | null>(null);

function useSelectContext(componentName: string): SelectContextValue {
  const ctx = useContext(SelectContext);
  if (!ctx) throw new Error(`Select.${componentName} deve ser usado dentro de <Select>`);
  return ctx;
}

function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();
}

function nextEnabledIndex(options: SelectOption[], current: number, direction: 1 | -1): number {
  if (options.length === 0) return -1;
  let index = current;
  for (let step = 0; step < options.length; step += 1) {
    index = (index + direction + options.length) % options.length;
    if (!options[index]?.disabled) return index;
  }
  return current;
}

type SelectCommonProps = {
  options: SelectOption[];
  children: ReactNode;
  disabled?: boolean;
  placeholder?: string;
  id?: string;
  /** Busca controlada (modo assíncrono/servidor): quando presente, o Select não filtra
   * `options` localmente — assume que já chegam filtradas (ver `useAsyncOptions`). */
  query?: string;
  onQueryChange?: (query: string) => void;
  status?: SelectStatus;
  errorMessage?: string;
  hasMore?: boolean;
  onLoadMore?: () => void;
};

type SelectSingleProps = SelectCommonProps & {
  multiple?: false;
  value: string | null;
  onChange: (value: string | null) => void;
};

type SelectMultipleProps = SelectCommonProps & {
  multiple: true;
  value: string[];
  onChange: (value: string[]) => void;
};

export type SelectProps = SelectSingleProps | SelectMultipleProps;

export function Select(props: SelectProps) {
  const {
    options,
    children,
    disabled = false,
    placeholder,
    id,
    query: controlledQuery,
    onQueryChange,
    status: controlledStatus,
    errorMessage,
    hasMore = false,
    onLoadMore,
  } = props;

  const isServerFiltered = onQueryChange !== undefined;
  const [open, setOpen] = useState(false);
  const [internalQuery, setInternalQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(-1);
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const generatedId = useId();
  const baseId = id ?? generatedId;
  const listboxId = `${baseId}-listbox`;
  const inputId = `${baseId}-input`;

  const query = isServerFiltered ? (controlledQuery ?? '') : internalQuery;

  function setQuery(next: string) {
    if (isServerFiltered) {
      onQueryChange?.(next);
    } else {
      setInternalQuery(next);
    }
    setActiveIndex(-1);
    setOpen(true);
  }

  const clearQuery = () => {
    setInternalQuery('');
    onQueryChange?.('');
  };

  const visibleOptions = useMemo(() => {
    if (isServerFiltered) return options;
    if (!query.trim()) return options;
    const term = normalize(query);
    return options.filter((option) => normalize(option.searchValue ?? option.label).includes(term));
  }, [options, query, isServerFiltered]);

  const status: SelectStatus = controlledStatus ?? (visibleOptions.length === 0 ? 'empty' : 'success');

  const selectedValues = useMemo<string[]>(
    () => (props.multiple ? props.value : props.value === null ? [] : [props.value]),
    [props.multiple, props.value],
  );

  function isSelected(value: string): boolean {
    return selectedValues.includes(value);
  }

  const selectedOptions = useMemo(
    () => options.filter((option) => selectedValues.includes(option.value)),
    [options, selectedValues],
  );

  function selectOption(option: SelectOption) {
    if (option.disabled) return;
    if (props.multiple) {
      const already = isSelected(option.value);
      const next = already ? props.value.filter((v) => v !== option.value) : [...props.value, option.value];
      props.onChange(next);
      clearQuery();
      inputRef.current?.focus();
    } else {
      props.onChange(option.value);
      setOpen(false);
      clearQuery();
    }
  }

  function removeValue(value: string) {
    if (!props.multiple) return;
    props.onChange(props.value.filter((v) => v !== value));
  }

  function getOptionId(index: number) {
    return `${listboxId}-option-${index}`;
  }

  useEffect(() => {
    if (!open) setActiveIndex(-1);
  }, [open]);

  useEffect(() => {
    function handlePointerDown(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handlePointerDown);
    return () => document.removeEventListener('mousedown', handlePointerDown);
  }, []);

  const contextValue: SelectContextValue = {
    multiple: Boolean(props.multiple),
    disabled,
    open,
    setOpen,
    query,
    setQuery,
    visibleOptions,
    status,
    errorMessage,
    isSelected,
    selectOption,
    removeValue,
    selectedOptions,
    activeIndex,
    setActiveIndex,
    listboxId,
    inputId,
    getOptionId,
    inputRef,
    placeholder,
    hasMore,
    onLoadMore,
  };

  return (
    <SelectContext.Provider value={contextValue}>
      <div ref={containerRef} style={{ position: 'relative' }}>
        {children}
      </div>
    </SelectContext.Provider>
  );
}

type SelectTriggerProps = {
  'aria-label'?: string;
  'aria-labelledby'?: string;
};

Select.Trigger = function Trigger({ 'aria-label': ariaLabel, 'aria-labelledby': ariaLabelledBy }: SelectTriggerProps) {
  const {
    multiple,
    disabled,
    open,
    setOpen,
    query,
    setQuery,
    activeIndex,
    setActiveIndex,
    visibleOptions,
    selectOption,
    selectedOptions,
    removeValue,
    listboxId,
    inputId,
    getOptionId,
    inputRef,
    placeholder,
  } = useSelectContext('Trigger');

  const singleSelectedLabel = !multiple ? selectedOptions[0]?.label : undefined;
  const displayValue = multiple ? query : open ? query : (singleSelectedLabel ?? query);

  function handleFocus(event: React.FocusEvent<HTMLInputElement>) {
    if (disabled) return;
    setOpen(true);
    event.target.select();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (disabled) return;
    switch (event.key) {
      case 'ArrowDown': {
        event.preventDefault();
        if (!open) {
          setOpen(true);
          setActiveIndex(nextEnabledIndex(visibleOptions, -1, 1));
        } else {
          setActiveIndex(nextEnabledIndex(visibleOptions, activeIndex, 1));
        }
        break;
      }
      case 'ArrowUp': {
        event.preventDefault();
        if (!open) {
          setOpen(true);
          setActiveIndex(nextEnabledIndex(visibleOptions, 0, -1));
        } else {
          setActiveIndex(nextEnabledIndex(visibleOptions, activeIndex, -1));
        }
        break;
      }
      case 'Enter': {
        if (open && activeIndex >= 0 && visibleOptions[activeIndex]) {
          event.preventDefault();
          selectOption(visibleOptions[activeIndex]);
        }
        break;
      }
      case 'Escape': {
        if (open) {
          event.preventDefault();
          setOpen(false);
        }
        break;
      }
      case 'Backspace': {
        if (multiple && query === '' && selectedOptions.length > 0) {
          removeValue(selectedOptions[selectedOptions.length - 1].value);
        }
        break;
      }
      default:
        break;
    }
  }

  return (
    <div
      onClick={() => !disabled && inputRef.current?.focus()}
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        gap: 'var(--spacing-2xs)',
        border: '1px solid var(--color-border-default)',
        borderRadius: 'var(--radius-sm)',
        padding: 'var(--spacing-2xs) var(--spacing-sm)',
        background: disabled ? 'var(--color-bg-disabled)' : 'var(--color-bg-surface)',
        cursor: disabled ? 'not-allowed' : 'text',
      }}
    >
      {multiple &&
        selectedOptions.map((option) => (
          <span
            key={option.value}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 'var(--spacing-2xs)',
              background: 'var(--color-bg-inset)',
              borderRadius: 'var(--radius-full)',
              padding: `0 var(--spacing-sm)`,
              fontSize: 'var(--typography-size-sm)',
              color: 'var(--color-text-primary)',
            }}
          >
            {option.label}
            <button
              type="button"
              aria-label={`Remover ${option.label}`}
              disabled={disabled}
              onClick={(event) => {
                event.stopPropagation();
                removeValue(option.value);
              }}
              style={{
                border: 'none',
                background: 'transparent',
                cursor: disabled ? 'not-allowed' : 'pointer',
                lineHeight: 1,
                color: 'var(--color-text-secondary)',
                font: 'inherit',
              }}
            >
              ×
            </button>
          </span>
        ))}
      <input
        id={inputId}
        ref={inputRef}
        role="combobox"
        type="text"
        aria-expanded={open}
        aria-controls={listboxId}
        aria-haspopup="listbox"
        aria-autocomplete="list"
        aria-activedescendant={open && activeIndex >= 0 ? getOptionId(activeIndex) : undefined}
        aria-label={ariaLabel}
        aria-labelledby={ariaLabelledBy}
        disabled={disabled}
        placeholder={multiple ? (selectedOptions.length ? undefined : placeholder) : placeholder}
        value={displayValue}
        onChange={(event) => setQuery(event.target.value)}
        onFocus={handleFocus}
        onKeyDown={handleKeyDown}
        style={{
          flex: '1 1 auto',
          minWidth: '4ch',
          border: 'none',
          outline: 'none',
          font: 'inherit',
          fontSize: 'var(--typography-size-base)',
          background: 'transparent',
          color: 'var(--color-text-primary)',
        }}
      />
    </div>
  );
};

type SelectOptionsProps = {
  /** Render prop opcional para conteúdo customizado por opção; sem ela, usa `Select.Option`
   * (label simples) para cada item de `visibleOptions`. */
  children?: (option: SelectOption, index: number) => ReactNode;
};

Select.Options = function Options({ children }: SelectOptionsProps) {
  const { open, visibleOptions, status, errorMessage, listboxId, multiple, hasMore, onLoadMore } =
    useSelectContext('Options');

  if (!open) return null;

  return (
    <ul
      id={listboxId}
      role="listbox"
      aria-multiselectable={multiple || undefined}
      style={{
        position: 'absolute',
        insetInlineStart: 0,
        zIndex: 10,
        marginTop: 'var(--spacing-2xs)',
        maxHeight: '16rem',
        overflowY: 'auto',
        width: '100%',
        listStyle: 'none',
        padding: 'var(--spacing-2xs) 0',
        margin: 0,
        background: 'var(--color-bg-surface)',
        border: '1px solid var(--color-border-default)',
        borderRadius: 'var(--radius-sm)',
        boxShadow: '0 4px 12px rgba(0, 0, 0, 0.12)',
      }}
    >
      {status === 'loading' && visibleOptions.length === 0 && (
        <li
          role="status"
          aria-live="polite"
          style={{ padding: 'var(--spacing-sm)', color: 'var(--color-text-secondary)' }}
        >
          Carregando…
        </li>
      )}
      {status === 'error' && (
        <li role="alert" style={{ padding: 'var(--spacing-sm)', color: 'var(--color-state-danger-fg)' }}>
          {errorMessage ?? 'Não foi possível carregar as opções.'}
        </li>
      )}
      {status !== 'loading' && status !== 'error' && visibleOptions.length === 0 && (
        <li style={{ padding: 'var(--spacing-sm)', color: 'var(--color-text-secondary)' }}>
          Nenhuma opção encontrada.
        </li>
      )}
      {visibleOptions.map((option, index) =>
        children ? (
          children(option, index)
        ) : (
          <Select.Option key={option.value} option={option} index={index} />
        ),
      )}
      {status === 'loading' && visibleOptions.length > 0 && (
        <li
          role="status"
          aria-live="polite"
          style={{ padding: 'var(--spacing-sm)', color: 'var(--color-text-secondary)' }}
        >
          Carregando mais…
        </li>
      )}
      {status !== 'loading' && status !== 'error' && hasMore && onLoadMore && (
        <li>
          <button
            type="button"
            onClick={onLoadMore}
            style={{
              width: '100%',
              padding: 'var(--spacing-sm)',
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              font: 'inherit',
              color: 'var(--color-brand-default)',
            }}
          >
            Carregar mais
          </button>
        </li>
      )}
    </ul>
  );
};

type SelectOptionProps = {
  option: SelectOption;
  index: number;
  children?: ReactNode;
};

Select.Option = function Option({ option, index, children }: SelectOptionProps) {
  const { isSelected, selectOption, activeIndex, setActiveIndex, getOptionId } = useSelectContext('Option');
  const selected = isSelected(option.value);
  const active = activeIndex === index;

  return (
    <li
      id={getOptionId(index)}
      role="option"
      aria-selected={selected}
      aria-disabled={option.disabled || undefined}
      onMouseEnter={() => setActiveIndex(index)}
      onMouseDown={(event) => {
        // preventDefault mantém o foco no input do Trigger (evita blur antes do clique registrar).
        event.preventDefault();
        if (!option.disabled) selectOption(option);
      }}
      style={{
        padding: 'var(--spacing-sm)',
        cursor: option.disabled ? 'not-allowed' : 'pointer',
        color: option.disabled ? 'var(--color-neutral-500)' : 'var(--color-text-primary)',
        background: active ? 'var(--color-brand-subtle-bg)' : 'transparent',
        fontWeight: selected ? 'var(--typography-weight-medium)' : 'var(--typography-weight-regular)',
      }}
    >
      {children ?? option.label}
    </li>
  );
};
