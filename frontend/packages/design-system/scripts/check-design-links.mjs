// Gate de CI (RAZ-195): todo componente do design system com .stories.tsx
// precisa linkar pelo menos 1 frame Figma via parameters.design (addon-designs),
// senão a story documenta a API mas não a fidelidade visual contra o Figma.
// Componente sem .stories.tsx é gap de outro guardrail (README já documenta
// essa convenção) — não é responsabilidade deste script.
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const uiDir = path.join(__dirname, '..', 'src', 'ui');

const componentDirs = readdirSync(uiDir, { withFileTypes: true }).filter((entry) => entry.isDirectory());

const missing = [];
for (const dir of componentDirs) {
  const storyPath = path.join(uiDir, dir.name, `${dir.name}.stories.tsx`);
  let content;
  try {
    content = readFileSync(storyPath, 'utf-8');
  } catch {
    continue;
  }
  if (!/design:\s*\{/.test(content)) {
    missing.push(dir.name);
  }
}

if (missing.length > 0) {
  console.error(
    `[check-design-links] Componente(s) sem link Figma (parameters.design) em nenhuma story: ${missing.join(', ')}.\n` +
      'Node-id de cada componente: docs/arquitetura-tecnica/design-system-tokens-componentes.md §4 (ou src/figma-map.ts para os já prontos).',
  );
  process.exit(1);
}

console.log(`[check-design-links] OK — ${componentDirs.length} componente(s) com link Figma em pelo menos 1 story.`);
