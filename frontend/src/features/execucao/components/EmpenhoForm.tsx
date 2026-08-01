/**
 * Container (busca/monta estado via hook de api/) — presentational puro fica em
 * components/ menores; aqui a pagina de feature e o container (ADR-0032).
 *
 * Migrado para React Hook Form (RAZ-202/ADR-0043): este componente é só o DRIVING
 * ADAPTER — schema/validação vêm de `domain/empenhoSchema.ts` (derivado das invariantes
 * de `domain/empenho.ts`), o mapeamento campos→input e a chamada do caso de uso ficam em
 * `useFormDeAgregado` + `paraEmpenhoRequest`. O form nunca fala com `execucaoClient`
 * direto — só com `useCriarEmpenho`, que por sua vez chama o caso de uso.
 *
 * Campos == EmpenhoRequest REAL (escrita.EmpenhoController) — dotacaoId/credorId/
 * unidadeGestoraId/contratoId são UUID de cadastros que, numa tela completa, viriam de
 * autocomplete (fora do escopo de "1 tela mínima" — ver README "Gaps"; o wrapper para
 * esse cenário já existe em `shared/forms/campoSelectRHF.tsx`, sem consumidor ainda).
 * Aqui são texto livre (colar UUID), rotulado como tal.
 */
import { FormSection } from '@siafic/design-system';
import type { EmpenhoRequest, EmpenhoResponse } from '../../../shared/api/client';
import { useFormSectionRHF } from '../../../shared/forms/formSectionAdapterRHF';
import { useFormDeAgregado } from '../../../shared/forms/useFormDeAgregado';
import { paraEmpenhoRequest } from '../application/registrarEmpenho';
import { CAMPOS_EMPENHO_VAZIOS, empenhoSchema, type CamposEmpenho } from '../domain/empenhoSchema';
import { useCriarEmpenho } from '../api/useCriarEmpenho';

const OPCOES_TIPO = [
  { value: 'ordinario', label: 'Ordinário' },
  { value: 'estimativo', label: 'Estimativo' },
  { value: 'global', label: 'Global' },
];

export function EmpenhoForm() {
  const criarEmpenho = useCriarEmpenho();
  const { form, aoSubmeter } = useFormDeAgregado<CamposEmpenho, EmpenhoRequest, EmpenhoResponse>({
    schema: empenhoSchema,
    valoresIniciais: CAMPOS_EMPENHO_VAZIOS,
    paraInput: paraEmpenhoRequest,
    executar: (body) => criarEmpenho.mutateAsync(body),
  });
  const formSectionProps = useFormSectionRHF(form);
  const { isSubmitting, isSubmitSuccessful, errors } = form.formState;
  const erroDeEnvio = errors.root?.envio?.message;

  return (
    <form onSubmit={aoSubmeter} noValidate aria-label="Registrar empenho">
      <FormSection legend="Empenho" {...formSectionProps}>
        <FormSection.Field name="dotacaoId" label="ID da dotação (UUID)" required />
        <FormSection.Error name="dotacaoId" />

        <FormSection.Select name="tipo" label="Tipo" required options={OPCOES_TIPO} />
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

      {erroDeEnvio && (
        <p role="alert" style={{ color: 'var(--color-state-danger-fg)' }}>
          Não foi possível registrar o empenho: {erroDeEnvio}
        </p>
      )}

      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Registrando…' : 'Registrar empenho'}
      </button>
      <span role="status" aria-live="polite" style={{ marginLeft: 'var(--spacing-md)' }}>
        {isSubmitSuccessful ? 'Empenho registrado.' : ''}
      </span>
    </form>
  );
}
