/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // RAZ-221/ADR-0052: `npm run dev` (VITE_API_MODE=real, VITE_API_BASE_URL vazio) fica na
    // mesma origem do backend do ponto de vista do navegador — o cookie de sessão `Secure`
    // (ADR-0035) e o cookie anti-CSRF trafegam em HTTP local sem exigir HTTPS nem abrir
    // `CORS_ALLOWED_ORIGINS` (que segue desligado por padrão, CorsWebConfiguration.java).
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/sessao': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
  },
})
