import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './AuthContext';
import { MOTIVO_SESSAO_EXPIRADA, type EstadoRedirecionamentoLogin } from './redirecionamentoLogin';

export function RequireAuth({ children }: { children: ReactNode }) {
  const { sessao, carregando, sessaoExpirada } = useAuth();
  const location = useLocation();

  // Espera a hidratação inicial (GET /sessao/atual, RAZ-205) antes de decidir: senão todo
  // acesso direto a uma rota protegida (ex.: redirect de volta do gov.br) bate no `!sessao`
  // ainda null e manda pro /entrar mesmo com cookie de sessão válido.
  if (carregando) {
    return (
      <p role="status" aria-live="polite">
        Carregando sessão…
      </p>
    );
  }

  if (!sessao) {
    // Distingue "sessão expirou no meio do uso" de "nunca logou" (RAZ-239): só o primeiro
    // leva `motivo`, que `LoginPage` traduz no Alert "Sua sessão expirou".
    const state: EstadoRedirecionamentoLogin = {
      from: location,
      ...(sessaoExpirada ? { motivo: MOTIVO_SESSAO_EXPIRADA } : {}),
    };
    return <Navigate to="/entrar" replace state={state} />;
  }

  return <>{children}</>;
}
