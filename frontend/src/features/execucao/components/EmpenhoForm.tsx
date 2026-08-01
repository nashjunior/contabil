/**
 * Container (busca/monta estado via hook de api/) — presentational puro fica em
 * components/ menores; aqui a pagina de feature e o container (ADR-0032).
 *
 * Campos == EmpenhoRequest REAL (escrita.EmpenhoController) — dotacaoId/credorId/
 * unidadeGestoraId/contratoId são UUID de cadastros. `dotacaoId` já tem consulta própria
 * (`GET /execucao/dotacoes`, RAZ-148/ADR-0038) e usa o combo `DotacaoPicker` (RAZ-199).
 * credorId/unidadeGestoraId/contratoId ainda não têm endpoint de consulta no backend (só
 * `ConsultarDotacoes` existe hoje) — permanecem texto livre (colar UUID), fora do escopo
 * de "1 tela mínima", rotulados como tal — ver README "Gaps".
 */
import { useState, type FormEvent } from 'react';
import { FormSection } from '@siafic/design-system';
import { toMoney, isValidMoney } from '../../../shared/lib/dinheiro';
import { useCriarEmpenho } from '../api/useCriarEmpenho';
import { DotacaoPicker } from './DotacaoPicker';

type Campos = {
  dotacaoId: string;
  tipo: string;
  credorId: string;
  unidadeGestoraId: string;
  contratoId: string;
  valor: string;
  dataFato: string;
  exercicio: string;
  classificacaoOrcamentaria: string;
  fonteRecurso: string;
  historico: string;
};

const ANO_ATUAL = String(new Date().getFullYear());

const VAZIO: Campos = {
  dotacaoId: '',
  tipo: '',
  credorId: '',
  unidadeGestoraId: '',
  contratoId: '',
  valor: '',
  dataFato: '',
  exercicio: ANO_ATUAL,
  classificacaoOrcamentaria: '',
  fonteRecurso: '',
  historico: '',
};

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function EmpenhoForm() {
  const [values, setValues] = useState<Campos>(VAZIO);
  const [errors, setErrors] = useState<Record<string, string | undefined>>({});
  const criarEmpenho = useCriarEmpenho();

  function onChange(name: string, value: string) {
    setValues((atual) => ({ ...atual, [name]: value }));
  }

  function validar(): boolean {
    const e: Record<string, string | undefined> = {};
    if (!values.dotacaoId) e.dotacaoId = 'Selecione uma dotação.';
    if (!values.tipo) e.tipo = 'Selecione o tipo.';
    if (!UUID_PATTERN.test(values.credorId)) e.credorId = 'Informe um UUID válido.';
    if (!UUID_PATTERN.test(values.unidadeGestoraId)) e.unidadeGestoraId = 'Informe um UUID válido.';
    if (values.contratoId && !UUID_PATTERN.test(values.contratoId)) e.contratoId = 'Informe um UUID válido (ou deixe em branco).';
    if (!isValidMoney(values.valor.replace(',', '.'))) e.valor = 'Informe um valor decimal válido (ex.: 1000.00).';
    if (!values.dataFato) e.dataFato = 'Informe a data do fato.';
    if (!/^\d{4}$/.test(values.exercicio)) e.exercicio = 'Informe o exercício (aaaa).';
    if (!values.classificacaoOrcamentaria.trim()) e.classificacaoOrcamentaria = 'Informe a classificação orçamentária.';
    if (!values.fonteRecurso.trim()) e.fonteRecurso = 'Informe a fonte de recurso.';
    if (!values.historico.trim()) e.historico = 'Informe o histórico.';
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!validar()) return;

    criarEmpenho.mutate(
      {
        dotacaoId: values.dotacaoId,
        tipo: values.tipo as 'ordinario' | 'estimativo' | 'global',
        credorId: values.credorId,
        unidadeGestoraId: values.unidadeGestoraId,
        contratoId: values.contratoId || null,
        valor: toMoney(values.valor),
        dataFato: values.dataFato,
        exercicio: Number(values.exercicio),
        classificacaoOrcamentaria: values.classificacaoOrcamentaria,
        fonteRecurso: values.fonteRecurso,
        historico: values.historico,
      },
      { onSuccess: () => setValues(VAZIO) },
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate aria-label="Registrar empenho">
      <FormSection legend="Empenho" values={values} errors={errors} onChange={onChange}>
        <FormSection.CustomField name="dotacaoId" label="Dotação" required>
          {(inputId) => (
            <DotacaoPicker
              key={values.exercicio}
              id={inputId}
              exercicio={Number(values.exercicio) || Number(ANO_ATUAL)}
              value={values.dotacaoId || null}
              onChange={(value) => onChange('dotacaoId', value ?? '')}
              ariaLabel="Dotação"
            />
          )}
        </FormSection.CustomField>
        <FormSection.Error name="dotacaoId" />

        <FormSection.Select
          name="tipo"
          label="Tipo"
          required
          options={[
            { value: 'ordinario', label: 'Ordinário' },
            { value: 'estimativo', label: 'Estimativo' },
            { value: 'global', label: 'Global' },
          ]}
        />
        <FormSection.Error name="tipo" />

        <FormSection.Field name="credorId" label="ID do credor (UUID)" required />
        <FormSection.Error name="credorId" />

        <FormSection.Field name="unidadeGestoraId" label="ID da unidade gestora (UUID)" required />
        <FormSection.Error name="unidadeGestoraId" />

        <FormSection.Field name="contratoId" label="ID do contrato (UUID, opcional)" />
        <FormSection.Error name="contratoId" />

        <FormSection.Field name="valor" label="Valor (R$)" required inputMode="decimal" />
        <FormSection.Error name="valor" />

        <FormSection.Field name="dataFato" label="Data do fato" type="date" required />
        <FormSection.Error name="dataFato" />

        <FormSection.Field name="exercicio" label="Exercício" required inputMode="numeric" />
        <FormSection.Error name="exercicio" />

        <FormSection.Field name="classificacaoOrcamentaria" label="Classificação orçamentária" required />
        <FormSection.Error name="classificacaoOrcamentaria" />

        <FormSection.Field name="fonteRecurso" label="Fonte de recurso" required />
        <FormSection.Error name="fonteRecurso" />

        <FormSection.Field name="historico" label="Histórico" required />
        <FormSection.Error name="historico" />
      </FormSection>

      {criarEmpenho.isError && (
        <p role="alert" style={{ color: 'var(--color-state-danger-fg)' }}>
          Não foi possível registrar o empenho: {criarEmpenho.error.message}
        </p>
      )}

      <button type="submit" disabled={criarEmpenho.isPending}>
        {criarEmpenho.isPending ? 'Registrando…' : 'Registrar empenho'}
      </button>
      <span role="status" aria-live="polite" style={{ marginLeft: 'var(--spacing-md)' }}>
        {criarEmpenho.isSuccess ? 'Empenho registrado.' : ''}
      </span>
    </form>
  );
}
