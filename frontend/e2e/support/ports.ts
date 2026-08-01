/** Portas fixas do harness E2E (RAZ-211) — conhecidas em tempo de config, sem
 * descoberta dinâmica, porque `vite dev` (webServer do Playwright) precisa de
 * `VITE_API_BASE_URL` já no boot (Vite não recarrega `import.meta.env` em runtime)
 * e o `webServer` do Playwright é configurado antes do `global-setup.ts` rodar. */
export const BACKEND_PORT = 18080;
export const FRONTEND_PORT = 4173;
