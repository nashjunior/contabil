// Client de API tipado, contract-first (ADR-0033). Nenhum tipo de request/response e escrito a
// mao: gerado por openapi-typescript a partir do contrato OpenAPI.
//
// Fonte por padrao: openapi/contrato-provisorio.yaml (marcado x-status no proprio arquivo —
// motivo em ADR-0033 e no comentario do arquivo). Quando o backend expuser springdoc-openapi
// (`/v3/api-docs`), trocar a fonte: `npm run api:generate -- --input http://localhost:8080/v3/api-docs`.
import openapiTS, { astToString } from 'openapi-typescript';
import { writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const inputFlagIndex = args.indexOf('--input');
const input =
  inputFlagIndex >= 0 && args[inputFlagIndex + 1]
    ? args[inputFlagIndex + 1]
    : path.join(__dirname, '..', 'openapi', 'contrato-provisorio.yaml');

const outDir = path.join(__dirname, '..', 'src', 'shared', 'api', 'generated');
mkdirSync(outDir, { recursive: true });

const isRemote = /^https?:\/\//.test(input);
const ast = await openapiTS(isRemote ? new URL(input) : pathToFileURL(path.resolve(input)));
const contents = astToString(ast);

const header = `// GERADO por \`npm run api:generate\` — nao editar a mao.\n// Fonte: ${input}\n\n`;
writeFileSync(path.join(outDir, 'schema.ts'), header + contents);
console.log(`[api:generate] gerado src/shared/api/generated/schema.ts a partir de ${input}`);
