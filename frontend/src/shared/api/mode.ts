/**
 * GAP (RAZ-120): nao ha, neste ambiente, um backend Spring Boot real acessivel
 * (multi-modulo Gradle + Postgres via Testcontainers/Docker nao provisionados
 * no scaffold). `VITE_API_MODE=mock` (default em dev) usa MSW contra o mesmo
 * contrato provisorio; `VITE_API_MODE=real` desliga o mock e chama a API real
 * — trocar quando houver um backend rodavel para provar o fim-a-fim real.
 */
export const apiMode: 'mock' | 'real' = import.meta.env.VITE_API_MODE === 'real' ? 'real' : 'mock';
