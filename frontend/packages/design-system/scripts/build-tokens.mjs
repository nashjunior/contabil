// Pipeline de tokens (ADR-0031/ADR-0033): tokens/*.tokens.json (fonte DTCG) -> src/tokens/theme.{ts,css}
import StyleDictionary from 'style-dictionary';

function toCamel(segment) {
  return segment.replace(/-([a-z0-9])/g, (_, c) => c.toUpperCase());
}

function buildNestedObject(tokens) {
  const root = {};
  for (const token of tokens) {
    let node = root;
    const path = token.path.map(toCamel);
    for (let i = 0; i < path.length - 1; i += 1) {
      node[path[i]] ??= {};
      node = node[path[i]];
    }
    node[path[path.length - 1]] = token.$value;
  }
  return root;
}

function serialize(value, indent = 1) {
  const pad = '  '.repeat(indent);
  const closePad = '  '.repeat(indent - 1);
  if (Array.isArray(value)) {
    return `[${value.map((v) => JSON.stringify(v)).join(', ')}]`;
  }
  if (value !== null && typeof value === 'object') {
    const entries = Object.entries(value)
      .map(([key, v]) => `${pad}${/^[a-zA-Z_$][\w$]*$/.test(key) ? key : JSON.stringify(key)}: ${serialize(v, indent + 1)},`)
      .join('\n');
    return `{\n${entries}\n${closePad}}`;
  }
  return JSON.stringify(value);
}

const sd = new StyleDictionary({
  source: ['tokens/**/*.tokens.json'],
  usesDtcg: true,
  log: { verbosity: 'verbose' },
  hooks: {
    formats: {
      'typescript/theme': ({ dictionary }) => {
        const theme = buildNestedObject(dictionary.allTokens);
        return `// GERADO por \`npm run tokens:build\` — nao editar a mao.
// Fonte: packages/design-system/tokens/*.tokens.json (ADR-0031/ADR-0033).
// Enquanto RAZ-100 (Paula) nao entrega os tokens reais, estes valores sao placeholder explicito.

export const theme = ${serialize(theme)} as const;

export type Theme = typeof theme;
`;
      },
    },
  },
  platforms: {
    ts: {
      transforms: ['name/camel'],
      buildPath: 'src/tokens/',
      files: [{ destination: 'theme.ts', format: 'typescript/theme' }],
    },
    css: {
      transformGroup: 'css',
      buildPath: 'src/tokens/',
      files: [
        {
          destination: 'theme.css',
          format: 'css/variables',
          options: { selector: ':root', outputReferences: true },
        },
      ],
    },
  },
});

await sd.hasInitialized;
await sd.buildAllPlatforms();
