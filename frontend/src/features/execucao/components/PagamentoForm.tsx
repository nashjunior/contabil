/**
 * Container (ADR-0032) — mesmo padrão RHF+zod de `EmpenhoForm.tsx`/`LiquidacaoForm.tsx`
 * (RAZ-202/ADR-0043/RAZ-230). `liquidacaoId` vem do `LiquidacaoAprovadaPicker`, ligado ao RHF
 * via `Controller`. Beneficiário nominal fica sempre visível (não escondido por `natureza`):
 * a regra "obrigatório exceto folha consolidada" já é aplicada pelo schema
 * (`pagamentoSchema.ts`, `superRefine`) e a mensagem de erro por campo já deixa claro quando é
 * exigida — esconder/mostrar o bloco dinamicamente por `natureza` é decisão de UX que caberia à
 * Paula, mesmo racional do gate de aprovação (RAZ-230, item 3), fora do escopo desta issue.
 */
import { FormSection } from '@siafic/design-system';
import { Controller } from 'react-hook-form';
import type { PagamentoRequest, PagamentoResponse } from '../../../shared/api/client';
import { useFormSectionRHF } from '../../../shared/forms/formSectionAdapterRHF';
import { useFormDeAgregado } from '../../../shared/forms/useFormDeAgregado';
import { paraPagamentoRequest } from '../application/registrarPagamento';
import { CAMPOS_PAGAMENTO_VAZIOS, pagamentoSchema, type CamposPagamento } from '../domain/pagamentoSchema';
import { useRegistrarPagamento } from '../api/useRegistrarPagamento';
import { LiquidacaoAprovadaPicker } from './LiquidacaoAprovadaPicker';

const OPCOES_NATUREZA = [
  { value: 'orcamentario', label: 'Orçamentário' },
  { value: 'folha_consolidada', label: 'Folha consolidada' },
];

export function PagamentoForm() {
  const registrarPagamento = useRegistrarPagamento();
  const { form, aoSubmeter } = useFormDeAgregado<CamposPagamento, PagamentoRequest, PagamentoResponse>({
    schema: pagamentoSchema,
    valoresIniciais: CAMPOS_PAGAMENTO_VAZIOS,
    paraInput: paraPagamentoRequest,
    executar: (body) => registrarPagamento.mutateAsync(body),
  });
  const formSectionProps = useFormSectionRHF(form);
  const { isSubmitting, isSubmitSuccessful, errors } = form.formState;
  const erroDeEnvio = errors.root?.envio?.message;

  return (
    <form onSubmit={aoSubmeter} noValidate aria-label="Registrar pagamento">
      <FormSection legend="Pagamento" {...formSectionProps}>
        <FormSection.CustomField name="liquidacaoId" label="Liquidação" required>
          {(inputId) => (
            <Controller
              name="liquidacaoId"
              control={form.control}
              render={({ field }) => (
                <LiquidacaoAprovadaPicker id={inputId} value={field.value || null} onChange={(value) => field.onChange(value ?? '')} />
              )}
            />
          )}
        </FormSection.CustomField>
        <FormSection.Error name="liquidacaoId" />

        <FormSection.Select name="natureza" label="Natureza" required options={OPCOES_NATUREZA} />
        <FormSection.Error name="natureza" />

        <FormSection.Field name="dataCompetencia" label="Data de competência" type="date" required />
        <FormSection.Error name="dataCompetencia" />

        <FormSection.Field name="valor" label="Valor (R$)" required inputMode="decimal" />
        <FormSection.Error name="valor" />

        <FormSection.Field name="beneficiarioNome" label="Nome do beneficiário" />
        <FormSection.Error name="beneficiarioNome" />

        <FormSection.Field name="beneficiarioCpfCnpj" label="CPF/CNPJ do beneficiário" />
        <FormSection.Error name="beneficiarioCpfCnpj" />

        <FormSection.Field name="ordemBancaria" label="Ordem bancária (opcional)" />
        <FormSection.Error name="ordemBancaria" />

        <FormSection.Field name="historico" label="Histórico" required />
        <FormSection.Error name="historico" />
      </FormSection>

      {erroDeEnvio && (
        <p role="alert" style={{ color: 'var(--color-state-danger-fg)' }}>
          Não foi possível registrar o pagamento: {erroDeEnvio}
        </p>
      )}

      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Registrando…' : 'Registrar pagamento'}
      </button>
      <span role="status" aria-live="polite" style={{ marginLeft: 'var(--spacing-md)' }}>
        {isSubmitSuccessful ? 'Pagamento registrado.' : ''}
      </span>
    </form>
  );
}
