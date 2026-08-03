/**
 * `Barra de Filtros — Transparência (compartilhada Lista/Totais)` (Figma node 197:2,
 * design-system-tokens-componentes.md §6.1) — UM único componente instanciado nas Telas 1 e
 * 1b (ADR-0060 decisão 3/UX-2), não dois filtros que podem divergir. Controlado: o rascunho
 * só vira consulta real ao clicar "Aplicar filtros" (mesmo padrão do wireframe — não filtra a
 * cada tecla), e resincroniza com `valor` quando o filtro muda por fora (drill-down da Tela
 * 1b, "Limpar filtros", navegação com filtro na URL).
 */
import { useState, type CSSProperties, type FormEvent } from 'react';
import { CAMPOS_ORDENACAO, ESTAGIOS_FILTRAVEIS, rotuloEstagio } from '../domain/estagio';
import { FILTRO_VAZIO, type FiltroFormState } from '../domain/filtro';

const rotuloCampoStyle: CSSProperties = {
  display: 'block',
  marginBottom: 'var(--spacing-2xs)',
  fontSize: 'var(--typography-size-sm)',
  fontWeight: 'var(--typography-weight-medium)',
  color: 'var(--color-text-secondary)',
  textTransform: 'uppercase',
  letterSpacing: '0.4px',
};

const controleCampoStyle: CSSProperties = {
  boxSizing: 'border-box',
  border: '1px solid var(--color-border-default)',
  borderRadius: 'var(--radius-sm)',
  padding: 'var(--spacing-sm) var(--spacing-md)',
  fontSize: 'var(--typography-size-sm)',
  fontFamily: 'var(--typography-family-base)',
  color: 'var(--color-text-primary)',
  background: 'var(--color-bg-surface)',
  width: '100%',
};

const dicaStyle: CSSProperties = {
  margin: 'var(--spacing-2xs) 0 0',
  fontSize: '0.75rem',
  color: 'var(--color-text-tertiary)',
  maxWidth: 220,
};

function chipStyle(ativo: boolean): CSSProperties {
  return {
    padding: 'var(--spacing-2xs) var(--spacing-md)',
    borderRadius: 'var(--radius-full)',
    border: ativo ? '2px solid var(--color-brand-default)' : '1px solid var(--color-border-default)',
    background: ativo ? 'var(--color-brand-subtle-bg)' : 'var(--color-bg-surface)',
    color: ativo ? 'var(--color-text-link)' : 'var(--color-text-secondary)',
    fontSize: 'var(--typography-size-sm)',
    fontWeight: 'var(--typography-weight-medium)',
    letterSpacing: '0.4px',
    cursor: 'pointer',
  };
}

const botaoSecundarioStyle: CSSProperties = {
  background: 'var(--color-bg-surface)',
  color: 'var(--color-text-primary)',
  border: '1px solid var(--color-border-strong)',
};

export function BarraFiltros({
  valor,
  onAplicar,
  onLimpar,
}: {
  valor: FiltroFormState;
  onAplicar: (filtro: FiltroFormState) => void;
  onLimpar: () => void;
}) {
  // Resincroniza quando o filtro muda por fora (drill-down/limpar/navegação) via `key`
  // (`ListaTotaisPage` passa `key={JSON.stringify(filtroForm)}`) — remonta com um novo estado
  // inicial só quando o CONTEÚDO do filtro muda de verdade, sem precisar de um `useEffect`
  // reagindo a uma prop recalculada a cada render do pai a partir da URL.
  const [rascunho, setRascunho] = useState(valor);

  function set<K extends keyof FiltroFormState>(campo: K, novoValor: FiltroFormState[K]) {
    setRascunho((atual) => ({ ...atual, [campo]: novoValor }));
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    onAplicar(rascunho);
  }

  function handleLimpar() {
    setRascunho(FILTRO_VAZIO);
    onLimpar();
  }

  return (
    <form
      onSubmit={handleSubmit}
      aria-label="Filtros de despesas públicas"
      style={{
        background: 'var(--color-bg-surface)',
        border: '1px solid var(--color-border-default)',
        borderRadius: 'var(--radius-md)',
        padding: 'var(--spacing-lg) var(--spacing-xl)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--spacing-md)',
      }}
    >
      <div>
        <span style={rotuloCampoStyle}>Estágio</span>
        <div role="radiogroup" aria-label="Estágio" style={{ display: 'flex', gap: 'var(--spacing-sm)', flexWrap: 'wrap' }}>
          <button type="button" role="radio" aria-checked={rascunho.estagio === ''} style={chipStyle(rascunho.estagio === '')} onClick={() => set('estagio', '')}>
            Todos
          </button>
          {ESTAGIOS_FILTRAVEIS.map((estagio) => (
            <button
              key={estagio}
              type="button"
              role="radio"
              aria-checked={rascunho.estagio === estagio}
              style={chipStyle(rascunho.estagio === estagio)}
              onClick={() => set('estagio', estagio)}
            >
              {rotuloEstagio(estagio)}
            </button>
          ))}
        </div>
      </div>

      <div style={{ display: 'flex', gap: 'var(--spacing-md)', flexWrap: 'wrap', alignItems: 'start' }}>
        <div>
          <label htmlFor="filtro-data-inicio" style={rotuloCampoStyle}>
            Data início
          </label>
          <input
            id="filtro-data-inicio"
            type="date"
            value={rascunho.dataInicio}
            onChange={(e) => set('dataInicio', e.target.value)}
            style={{ ...controleCampoStyle, width: 160 }}
          />
        </div>
        <div>
          <label htmlFor="filtro-data-fim" style={rotuloCampoStyle}>
            Data fim
          </label>
          <input
            id="filtro-data-fim"
            type="date"
            value={rascunho.dataFim}
            onChange={(e) => set('dataFim', e.target.value)}
            style={{ ...controleCampoStyle, width: 160 }}
          />
        </div>
        <div>
          <label htmlFor="filtro-numero-empenho" style={rotuloCampoStyle}>
            Nº do empenho
          </label>
          <input
            id="filtro-numero-empenho"
            type="text"
            inputMode="numeric"
            placeholder="ex.: 1024"
            value={rascunho.numeroEmpenho}
            onChange={(e) => set('numeroEmpenho', e.target.value)}
            style={{ ...controleCampoStyle, width: 140 }}
          />
        </div>
        <div>
          <label htmlFor="filtro-funcao" style={rotuloCampoStyle}>
            Função de governo
          </label>
          <input
            id="filtro-funcao"
            type="text"
            placeholder="ex.: 10 — Saúde"
            value={rascunho.funcao}
            onChange={(e) => set('funcao', e.target.value)}
            style={{ ...controleCampoStyle, width: 200 }}
          />
          <p style={dicaStyle}>campo aceito pela API; sem descrição no dicionário-fonte ainda (ver Dicionário de dados)</p>
        </div>
        <div>
          <label htmlFor="filtro-ordenar-por" style={rotuloCampoStyle}>
            Ordenar por
          </label>
          <select
            id="filtro-ordenar-por"
            value={rascunho.ordenarPor}
            onChange={(e) => set('ordenarPor', e.target.value)}
            style={{ ...controleCampoStyle, width: 220 }}
          >
            {CAMPOS_ORDENACAO.map((campo) => (
              <option key={campo.valor} value={campo.valor}>
                {campo.rotulo}
              </option>
            ))}
          </select>
          <p style={dicaStyle}>campos permitidos: publicadoEm, data, valor, numeroEmpenho</p>
        </div>
      </div>

      <div>
        <p style={{ ...dicaStyle, margin: '0 0 var(--spacing-xs)' }}>Avançado — cole um identificador (não é busca por nome)</p>
        <div style={{ display: 'flex', gap: 'var(--spacing-md)', flexWrap: 'wrap' }}>
          <div>
            <label htmlFor="filtro-credor-id" style={rotuloCampoStyle}>
              ID do credor
            </label>
            <input
              id="filtro-credor-id"
              type="text"
              placeholder="ex.: colado de um documento"
              value={rascunho.credorId}
              onChange={(e) => set('credorId', e.target.value)}
              style={{ ...controleCampoStyle, width: 220 }}
            />
          </div>
          <div>
            <label htmlFor="filtro-orgao-id" style={rotuloCampoStyle}>
              ID do órgão
            </label>
            <input
              id="filtro-orgao-id"
              type="text"
              placeholder="ex.: colado de um documento"
              value={rascunho.orgaoId}
              onChange={(e) => set('orgaoId', e.target.value)}
              style={{ ...controleCampoStyle, width: 220 }}
            />
          </div>
          <div>
            <label htmlFor="filtro-contrato-id" style={rotuloCampoStyle}>
              ID do contrato
            </label>
            <input
              id="filtro-contrato-id"
              type="text"
              placeholder="ex.: colado de um documento"
              value={rascunho.contratoId}
              onChange={(e) => set('contratoId', e.target.value)}
              style={{ ...controleCampoStyle, width: 220 }}
            />
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 'var(--spacing-sm)' }}>
        <button type="submit">Aplicar filtros</button>
        <button type="button" onClick={handleLimpar} style={botaoSecundarioStyle}>
          Limpar filtros
        </button>
      </div>
    </form>
  );
}
