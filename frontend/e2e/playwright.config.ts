/**
 * RAZ-211 — E2E de navegador real (frontend real + backend real + Postgres real,
 * ver `global-setup.ts`). Vive fora de `src/` de propósito: não é `tsc -b`/`vitest`
 * (nomeado `*.pw.ts`, não `*.test.ts`/`*.spec.ts`, para não colidir com o glob
 * default do Vitest em `vite.config.ts`) nem faz parte do bundle do SPA.
 */
import { defineConfig, devices } from '@playwright/test';
import { BACKEND_PORT, FRONTEND_PORT } from './support/ports';

export default defineConfig({
  testDir: './specs',
  testMatch: '**/*.pw.ts',
  globalSetup: './global-setup.ts',
  fullyParallel: false, // specs compartilham o mesmo backend/Postgres — dados semeados uma vez.
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://localhost:${FRONTEND_PORT}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev -- --port ' + FRONTEND_PORT + ' --strictPort',
    cwd: '..',
    url: `http://localhost:${FRONTEND_PORT}`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    env: {
      VITE_API_MODE: 'real',
      VITE_API_BASE_URL: `http://localhost:${BACKEND_PORT}`,
    },
  },
});
