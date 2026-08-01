/**
 * Decisão do gate 4-eyes (ADR-0055) — disparado pelo botão "Decidir" de uma linha pendente em
 * `FilaAprovacaoList.tsx`. Confirmação = o próprio resumo (valor, competência, trilha compacta
 * via `useTrilhaLiquidacao`, já existente) mais o clique explícito no botão de decisão; SEM
 * segundo modal "tem certeza?" (decisão 2 — dialog-on-dialog é fadiga de confirmação, o oposto
 * do que uma ação irreversível precisa). Motivo só aparece/é obrigatório na variante devolver
 * (decisão 3, `decisaoAprovacaoSchema`). Erro mapeado por `erro.codigo` (decisão 4,
 * `mensagemAmigavelDecisao`) — os códigos que tornam o item não mais decidível fecham o modal
 * (a fila já foi invalidada por `useAprovarLiquidacao`); os demais mantêm o modal aberto com o
 * erro inline, sem esconder o botão (não há dado pra prever `auto_aprovacao_vedada`/`sem_permissao`
 * antes do clique — risco residual aceito na decisão).
 */
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Alert } from '@siafic/design-system';
import { ApiError, type ItemFilaAprovacao } from '../../../shared/api/client';
import { formatMoneyBRL } from '../../../shared/lib/dinheiro';
import { Modal } from '../../../shared/components/Modal';
import { useAprovarLiquidacao } from '../api/useAprovarLiquidacao';
import { useTrilhaLiquidacao } from '../api/useTrilhaLiquidacao';
import { CODIGOS_ERRO_QUE_REMOVEM_ITEM_DA_FILA, mensagemAmigavelDecisao, paraAprovacaoRequest } from '../application/aprovarLiquidacao';
import { CAMPOS_DECISAO_APROVACAO_VAZIOS, decisaoAprovacaoSchema, type CamposDecisaoAprovacao } from '../domain/decisaoAprovacaoSchema';
import { TrilhaLiquidacaoList } from './TrilhaLiquidacaoList';

const TITULO_ID = 'gate-aprovacao-titulo';
const MOTIVO_ID = 'gate-aprovacao-motivo';

export type GateAprovacaoModalProps = {
  item: ItemFilaAprovacao;
  onFechar: () => void;
};

export function GateAprovacaoModal({ item, onFechar }: GateAprovacaoModalProps) {
  const trilha = useTrilhaLiquidacao(item.id);
  const decidir = useAprovarLiquidacao();
  const form = useForm<CamposDecisaoAprovacao>({
    resolver: zodResolver(decisaoAprovacaoSchema),
    defaultValues: CAMPOS_DECISAO_APROVACAO_VAZIOS,
  });
  const decisaoAtual = form.watch('decisao');
  const { isSubmitting, errors } = form.formState;
  const erroDeEnvio = errors.root?.envio?.message;

  function mostrarDevolucao() {
    form.setValue('decisao', 'devolver');
  }

  function cancelarDevolucao() {
    form.setValue('decisao', 'aprovar');
    form.setValue('motivo', '');
    form.clearErrors('motivo');
  }

  const aoSubmeter = form.handleSubmit(async (campos) => {
    try {
      await decidir.mutateAsync({ liquidacaoId: item.id, body: paraAprovacaoRequest(campos) });
      onFechar();
    } catch (erro) {
      form.setError('root.envio', { type: 'envio', message: mensagemAmigavelDecisao(erro) });
      if (erro instanceof ApiError && CODIGOS_ERRO_QUE_REMOVEM_ITEM_DA_FILA.has(erro.codigo)) {
        onFechar();
      }
    }
  });

  return (
    <Modal titleId={TITULO_ID} onClose={onFechar}>
      <h2 id={TITULO_ID}>Decidir liquidação</h2>

      <p>
        <strong>Empenho:</strong> {item.exercicioEmpenho}/{item.numeroEmpenho}
      </p>
      <p>
        <strong>Valor:</strong> {formatMoneyBRL(item.valor)}
      </p>
      <p>
        <strong>Data de competência:</strong>{' '}
        <time dateTime={item.dataCompetencia}>{new Date(`${item.dataCompetencia}T00:00:00`).toLocaleDateString('pt-BR')}</time>
      </p>

      <section aria-labelledby="gate-aprovacao-trilha-titulo">
        <h3 id="gate-aprovacao-trilha-titulo">Trilha</h3>
        {trilha.isLoading ? <p role="status">Carregando…</p> : <TrilhaLiquidacaoList eventos={trilha.data?.eventos ?? []} />}
      </section>

      <form onSubmit={aoSubmeter} noValidate aria-label="Decidir liquidação">
        {decisaoAtual === 'devolver' && (
          <div>
            <label htmlFor={MOTIVO_ID}>Motivo da devolução (obrigatório)</label>
            <br />
            <textarea id={MOTIVO_ID} {...form.register('motivo')} />
            {errors.motivo && <p role="alert">{errors.motivo.message}</p>}
          </div>
        )}

        {erroDeEnvio && (
          <Alert level="danger">
            <Alert.Title>Não foi possível concluir a decisão</Alert.Title>
            <Alert.Body>{erroDeEnvio}</Alert.Body>
          </Alert>
        )}

        <p>Esta decisão é definitiva e não pode ser desfeita.</p>

        <div>
          {decisaoAtual === 'aprovar' ? (
            <>
              <button type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'Aprovando…' : 'Aprovar'}
              </button>
              <button type="button" onClick={mostrarDevolucao} disabled={isSubmitting}>
                Devolver…
              </button>
            </>
          ) : (
            <>
              <button type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'Devolvendo…' : 'Devolver'}
              </button>
              <button type="button" onClick={cancelarDevolucao} disabled={isSubmitting}>
                Cancelar
              </button>
            </>
          )}
          <button type="button" onClick={onFechar} disabled={isSubmitting}>
            Fechar
          </button>
        </div>
      </form>
    </Modal>
  );
}
