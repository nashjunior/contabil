/**
 * Ativado em dev via VITE_API_MODE=mock (ver shared/api/mode.ts) enquanto nao ha
 * backend real acessivel (gap sinalizado no RAZ-120: nenhum servidor Spring Boot
 * rodavel neste ambiente de scaffold).
 */
import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

export const worker = setupWorker(...handlers);
