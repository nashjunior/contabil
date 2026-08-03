/**
 * Renderiza o markdown de `GET /dicionario-dados` (UX-5/ADR-0059): "a mesma fonte servida
 * pela API, não um texto reescrito à parte" — texto vem 100% do backend, este componente só
 * traduz `#`/`##`/listas `- `/`` `código` `` para elementos semânticos. Parser deliberadamente
 * pequeno (não um pacote de markdown genérico): o subconjunto que `DICIONARIO_DADOS`
 * (`TransparenciaPublicaController.java`) usa é fixo e conhecido — H1/H2, parágrafo, lista,
 * código inline — não vale a dependência nova para isso. Nunca usa `dangerouslySetInnerHTML`:
 * o texto vem de rede (ainda que hoje seja uma constante do backend), então cada trecho vira
 * nó React explícito, não HTML injetado.
 *
 * O `# ` inicial (título) é omitido: a tela já mostra seu próprio `<h1>` de página com o mesmo
 * conteúdo (mesmo padrão do wireframe — Tela 3 tem o H1 da página SEPARADO da primeira linha
 * do corpo do markdown, que começa em "A API lê somente...").
 */
import type { ReactNode } from 'react';

function renderInline(texto: string, keyPrefix: string): ReactNode[] {
  return texto.split(/(`[^`]+`)/g).map((parte, indice) => {
    const chave = `${keyPrefix}-${indice}`;
    if (parte.startsWith('`') && parte.endsWith('`') && parte.length >= 2) {
      return <code key={chave}>{parte.slice(1, -1)}</code>;
    }
    return <span key={chave}>{parte}</span>;
  });
}

export function DicionarioMarkdown({ markdown }: { markdown: string }) {
  const blocos = markdown
    .trim()
    .split(/\n{2,}/)
    .map((bloco) =>
      bloco
        .split('\n')
        .map((linha) => linha.trim())
        .filter(Boolean),
    )
    .filter((linhas) => linhas.length > 0);

  const semTituloRedundante = blocos.length > 0 && blocos[0][0].startsWith('# ') ? blocos.slice(1) : blocos;

  return (
    <div>
      {semTituloRedundante.map((linhas, indiceBloco) => {
        if (linhas[0].startsWith('## ')) {
          return <h2 key={indiceBloco}>{renderInline(linhas[0].slice(3), `h-${indiceBloco}`)}</h2>;
        }
        if (linhas.every((linha) => linha.startsWith('- '))) {
          return (
            <ul key={indiceBloco}>
              {linhas.map((linha, indiceItem) => (
                <li key={indiceItem}>{renderInline(linha.slice(2), `li-${indiceBloco}-${indiceItem}`)}</li>
              ))}
            </ul>
          );
        }
        return <p key={indiceBloco}>{renderInline(linhas.join(' '), `p-${indiceBloco}`)}</p>;
      })}
    </div>
  );
}
