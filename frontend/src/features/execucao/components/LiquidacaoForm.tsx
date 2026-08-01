/**
 * Container (ADR-0032) — mesmo padrão RHF+zod de `EmpenhoForm.tsx` (RAZ-202/ADR-0043): schema/
 * validação em `domain/liquidacaoSchema.ts`, mapeamento campos→input em
 * `application/registrarLiquidacao.ts#paraLiquidacaoRequest`, envio via `useRegistrarLiquidacao`
 * (nunca `execucaoClient` direto). `empenhoId` vem do `EmpenhoPicker` (RAZ-230), ligado ao RHF
 * via `Controller` — mesmo padrão do `DotacaoPicker` no `EmpenhoForm`.
 *
 * `documentosSuporte` é o único campo array desta feature — a Lei 4.320/1964 art. 63 §2º exige
 * ao menos 1 documento por liquidação (`Liquidacao.validarDocumentosSuporte`). `useFieldArray`
 * do RHF cobre a lista; como cada linha é um objeto (não um `<input>` escalar), os campos da
 * linha usam `FormSection.CustomField` só pelo par label/id (ele não lê `values`/`errors` do
 * Context — só `FormSection.Field`/`.Select`/`.Error` leem, ver `FormSection.tsx`), com erro
 * exibido manualmente a partir de `form.formState.errors.documentosSuporte[i]`: o adaptador
 * `useFormSectionRHF` só cobre campo escalar top-level, não indexa erro por item de array.
 */
import { FormSection } from '@siafic/design-system';
import { Controller, useFieldArray } from 'react-hook-form';
import type { LiquidacaoRequest, LiquidacaoResponse } from '../../../shared/api/client';
import { useFormSectionRHF } from '../../../shared/forms/formSectionAdapterRHF';
import { useFormDeAgregado } from '../../../shared/forms/useFormDeAgregado';
import { paraLiquidacaoRequest } from '../application/registrarLiquidacao';
import { CAMPOS_LIQUIDACAO_VAZIOS, DOCUMENTO_SUPORTE_VAZIO, liquidacaoSchema, type CamposLiquidacao } from '../domain/liquidacaoSchema';
import { useRegistrarLiquidacao } from '../api/useRegistrarLiquidacao';
import { EmpenhoPicker } from './EmpenhoPicker';

const erroStyle = { color: 'var(--color-state-danger-fg)' } as const;

export function LiquidacaoForm() {
  const registrarLiquidacao = useRegistrarLiquidacao();
  const { form, aoSubmeter } = useFormDeAgregado<CamposLiquidacao, LiquidacaoRequest, LiquidacaoResponse>({
    schema: liquidacaoSchema,
    valoresIniciais: CAMPOS_LIQUIDACAO_VAZIOS,
    paraInput: paraLiquidacaoRequest,
    executar: (body) => registrarLiquidacao.mutateAsync(body),
  });
  const formSectionProps = useFormSectionRHF(form);
  const { isSubmitting, isSubmitSuccessful, errors } = form.formState;
  const erroDeEnvio = errors.root?.envio?.message;
  const erroDocumentos = typeof errors.documentosSuporte?.message === 'string' ? errors.documentosSuporte.message : undefined;
  const documentos = useFieldArray({ control: form.control, name: 'documentosSuporte' });

  return (
    <form onSubmit={aoSubmeter} noValidate aria-label="Registrar liquidação">
      <FormSection legend="Liquidação" {...formSectionProps}>
        <FormSection.CustomField name="empenhoId" label="Empenho" required>
          {(inputId) => (
            <Controller
              name="empenhoId"
              control={form.control}
              render={({ field }) => (
                <EmpenhoPicker id={inputId} value={field.value || null} onChange={(value) => field.onChange(value ?? '')} />
              )}
            />
          )}
        </FormSection.CustomField>
        <FormSection.Error name="empenhoId" />

        <FormSection.Field name="dataCompetencia" label="Data de competência" type="date" required />
        <FormSection.Error name="dataCompetencia" />

        <FormSection.Field name="valor" label="Valor (R$)" required inputMode="decimal" />
        <FormSection.Error name="valor" />

        <FormSection.Field name="historico" label="Histórico" required />
        <FormSection.Error name="historico" />
      </FormSection>

      {/* Sem `values`/`errors` reais (array não é campo escalar) — só reusa a casca visual do
          FormSection (fieldset + legend) e o `CustomField` pela geração de id/label. */}
      <FormSection legend="Documentos de suporte" values={{}} errors={{}} onChange={() => {}}>
        {erroDocumentos && (
          <p role="alert" style={erroStyle}>
            {erroDocumentos}
          </p>
        )}
        {documentos.fields.map((campo, index) => {
          const errosDoDocumento = errors.documentosSuporte?.[index];
          return (
            <div
              key={campo.id}
              role="group"
              aria-label={`Documento de suporte ${index + 1}`}
              style={{
                marginBottom: 'var(--spacing-md)',
                paddingBottom: 'var(--spacing-md)',
                borderBottom: '1px solid var(--color-border-default)',
              }}
            >
              <FormSection.CustomField name={`documentosSuporte.${index}.tipo`} label="Tipo do documento" required>
                {(inputId) => <input id={inputId} {...form.register(`documentosSuporte.${index}.tipo`)} />}
              </FormSection.CustomField>
              {errosDoDocumento?.tipo && <p role="alert" style={erroStyle}>{errosDoDocumento.tipo.message}</p>}

              <FormSection.CustomField name={`documentosSuporte.${index}.numero`} label="Número do documento" required>
                {(inputId) => <input id={inputId} {...form.register(`documentosSuporte.${index}.numero`)} />}
              </FormSection.CustomField>
              {errosDoDocumento?.numero && <p role="alert" style={erroStyle}>{errosDoDocumento.numero.message}</p>}

              <FormSection.CustomField name={`documentosSuporte.${index}.dataEmissao`} label="Data de emissão" required>
                {(inputId) => <input id={inputId} type="date" {...form.register(`documentosSuporte.${index}.dataEmissao`)} />}
              </FormSection.CustomField>
              {errosDoDocumento?.dataEmissao && <p role="alert" style={erroStyle}>{errosDoDocumento.dataEmissao.message}</p>}

              <FormSection.CustomField name={`documentosSuporte.${index}.referenciaExterna`} label="Referência externa (opcional)">
                {(inputId) => <input id={inputId} {...form.register(`documentosSuporte.${index}.referenciaExterna`)} />}
              </FormSection.CustomField>

              <button type="button" onClick={() => documentos.remove(index)} disabled={documentos.fields.length <= 1}>
                Remover documento {index + 1}
              </button>
            </div>
          );
        })}
        <button type="button" onClick={() => documentos.append(DOCUMENTO_SUPORTE_VAZIO)}>
          Adicionar documento de suporte
        </button>
      </FormSection>

      {erroDeEnvio && (
        <p role="alert" style={erroStyle}>
          Não foi possível registrar a liquidação: {erroDeEnvio}
        </p>
      )}

      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Registrando…' : 'Registrar liquidação'}
      </button>
      <span role="status" aria-live="polite" style={{ marginLeft: 'var(--spacing-md)' }}>
        {isSubmitSuccessful ? 'Liquidação registrada.' : ''}
      </span>
    </form>
  );
}
