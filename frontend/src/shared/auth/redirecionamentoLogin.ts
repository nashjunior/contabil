/**
 * Contrato do redirect para `/entrar` (RAZ-239). `RequireAuth` anexa este estado de
 * navegação; `LoginPage` o lê. Motivo é só client-side (sem contrato/backend, ADR-0035):
 * distingue "sessão que expirou/foi revogada no meio do uso" de "nunca logou" — hoje ambos
 * caíam no mesmo formulário sem contexto (gap #2 documentado na tela Entrar da RAZ-235,
 * `design-system-tokens-componentes.md`).
 */
export const MOTIVO_SESSAO_EXPIRADA = 'sessao_expirada' as const;

/** Estado anexado ao `<Navigate to="/entrar">` (RequireAuth → LoginPage). */
export type EstadoRedirecionamentoLogin = {
  /** Rota de origem, para voltar ao destino certo depois do login. */
  from?: { pathname?: string };
  /** Presente só quando a sessão caiu no meio do uso (não em "nunca logou"). */
  motivo?: typeof MOTIVO_SESSAO_EXPIRADA;
};
