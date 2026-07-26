import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './AuthContext';

export function RequireAuth({ children }: { children: ReactNode }) {
  const { sessao } = useAuth();
  const location = useLocation();

  if (!sessao) {
    return <Navigate to="/entrar" replace state={{ from: location }} />;
  }

  return <>{children}</>;
}
