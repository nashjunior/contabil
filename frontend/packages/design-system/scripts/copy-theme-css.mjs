// Copia dist/tokens/theme.css -> dist/theme.css apos o tsc build, para bater com o
// subpath export `@siafic/design-system/theme.css` (ver package.json `exports`).
import { copyFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const src = path.join(__dirname, '..', 'src', 'tokens', 'theme.css');
const destDir = path.join(__dirname, '..', 'dist');

mkdirSync(destDir, { recursive: true });
copyFileSync(src, path.join(destDir, 'theme.css'));
console.log('[ds:build] copiado theme.css -> dist/theme.css');
