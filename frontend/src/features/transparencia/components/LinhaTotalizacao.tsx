/**
 * `Linha de Totalização — Estágio` (Figma node 197:95) — reflete
 * `TotalizacaoTransparenciaPublica.Linha(estagio, valor, quantidade)` 1:1, só 3 linhas
 * possíveis + total geral (ADR-0060 decisão 6, nenhuma quebra por órgão/função inventada).
 * `onVerDocumentos` implementa o drill-down totalização→lista preservando os demais filtros
 * (ADR-0060 decisão 3).
 */
import type { TotalizacaoLinha } from '../../../shared/api/transparenciaClient';
import { BadgeEstagio } from './BadgeEstagio';
import { ValorMonetario } from './ValorMonetario';

export function LinhaTotalizacao({ linha, onVerDocumentos }: { linha: TotalizacaoLinha; onVerDocumentos: (estagio: string) => void }) {
  return (
    <tr>
      <td>
        <BadgeEstagio estagio={linha.estagio} />
      </td>
      <td>{linha.quantidade.toLocaleString('pt-BR')} documentos</td>
      <td>
        <ValorMonetario valor={linha.valor} />
      </td>
      <td>
        <button type="button" onClick={() => onVerDocumentos(linha.estagio)} style={{ background: 'transparent', color: 'var(--color-text-link)', border: 'none', padding: 0, fontSize: 'var(--typography-size-sm)' }}>
          Ver documentos deste estágio ›
        </button>
      </td>
    </tr>
  );
}
