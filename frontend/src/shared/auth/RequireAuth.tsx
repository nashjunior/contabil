import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './AuthContext';

export function RequireAuth({ children }: { children: ReactNode }) {
  const { sessao, carregando } = useAuth();
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
    return <Navigate to="/entrar" replace state={{ from: location }} />;
  }

  return <>{children}</>;
}
