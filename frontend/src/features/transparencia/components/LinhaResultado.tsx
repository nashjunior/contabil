/**
 * `Linha de Resultado — Despesa Pública` (Figma node 197:57) — implementada como `<tr>` (não
 * o `<div>` de grid do Figma): dado tabular real ganha semântica de tabela para leitor de
 * tela, piso eMAG/WCAG 2.2 AA desta issue (ADR-0060) pesa mais aqui que fidelidade literal de
 * markup do wireframe.
 *
 * DESVIO DELIBERADO do exemplo do Figma: a maquete mostra nome de credor resolvido
 * ("Construtora Alfa Ltda") numa linha `Empenhado`, mas o payload real de empenho só carrega
 * `credorId` opaco (sem nome) — mesmo gap 3 já documentado no ADR-0060/issue ("credorId/
 * orgaoId/contratoId são identificadores opacos, sem busca por nome"). Esta linha segue o
 * contrato real, não o texto ilustrativo da maquete: Credor/Órgão mostram o ID truncado
 * (não um nome fabricado) quando disponível, e "—" quando o estágio nem carrega o campo (só
 * `empenhado` tem `credorId`/`orgaoId`/nº do empenho — ver `domain/payloadDespesa.ts`). Nomes
 * reais só existem para pagamento com beneficiário pessoa física/jurídica (`beneficiario`).
 */
import { Link } from 'react-router-dom';
import type { ItemTransparencia } from '../../../shared/api/transparenciaClient';
import { BadgeEstagio } from './BadgeEstagio';
import { ValorMonetario } from './ValorMonetario';
import {
  beneficiarioDoPayload,
  credorIdDoPayload,
  dataDoPayload,
  estagioDoPayload,
  numeroEmpenhoDoPayload,
  orgaoIdDoPayload,
  valorDoPayload,
} from '../domain/payloadDespesa';
import { formatarDataIso, formatarInstante, truncarId } from '../domain/formatoExibicao';

const SEM_DADO = <span style={{ color: 'var(--color-text-tertiary)' }}>—</span>;

function CelulaCredor({ payload }: { payload: ItemTransparencia['payload'] }) {
  const beneficiario = beneficiarioDoPayload(payload);
  if (beneficiario?.nome) {
    return (
      <span>
        {beneficiario.nome}
        {beneficiario.documento ? ` · ${beneficiario.documento}` : ''}
      </span>
    );
  }
  const credorId = credorIdDoPayload(payload);
  if (credorId) {
    return (
      <span title={credorId} style={{ fontFamily: 'monospace', fontSize: '0.85em' }}>
        {truncarId(credorId)}
      </span>
    );
  }
  return SEM_DADO;
}

function CelulaOrgao({ payload }: { payload: ItemTransparencia['payload'] }) {
  const orgaoId = orgaoIdDoPayload(payload);
  if (!orgaoId) return SEM_DADO;
  return (
    <span title={orgaoId} style={{ fontFamily: 'monospace', fontSize: '0.85em' }}>
      {truncarId(orgaoId)}
    </span>
  );
}

export function LinhaResultado({ enteId, item }: { enteId: string; item: ItemTransparencia }) {
  const { payload } = item;
  const data = dataDoPayload(payload);
  const valor = valorDoPayload(payload);
  const numeroEmpenho = numeroEmpenhoDoPayload(payload);

  return (
    <tr>
      <td>
        <BadgeEstagio estagio={estagioDoPayload(payload)} />
      </td>
      <td>{data ? <time dateTime={data}>{formatarDataIso(data)}</time> : SEM_DADO}</td>
      <td>{valor ? <ValorMonetario valor={valor} /> : SEM_DADO}</td>
      <td>
        <CelulaCredor payload={payload} />
      </td>
      <td>
        <CelulaOrgao payload={payload} />
      </td>
      <td>{numeroEmpenho ?? SEM_DADO}</td>
      <td style={{ color: 'var(--color-text-tertiary)', fontSize: '0.85em' }}>
        <time dateTime={item.publicadoEm}>{formatarInstante(item.publicadoEm)}</time>
      </td>
      <td>
        <Link to={`/transparencia/${enteId}/despesas/${item.sequencia}`} state={{ item }} aria-label={`Ver detalhes do documento ${item.sequencia}`}>
          ›
        </Link>
      </td>
    </tr>
  );
}
