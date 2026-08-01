import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from '../shared/auth/LoginPage';
import { RequireAuth } from '../shared/auth/RequireAuth';
import { ExecucaoPage } from '../features/execucao';
import { BalancetePage, CatalogoContasPage, SaldoContaPage } from '../features/razao';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/entrar" element={<LoginPage />} />
      <Route
        path="/execucao"
        element={
          <RequireAuth>
            <ExecucaoPage />
          </RequireAuth>
        }
      />
      <Route
        path="/razao/saldo"
        element={
          <RequireAuth>
            <SaldoContaPage />
          </RequireAuth>
        }
      />
      <Route
        path="/razao/balancete"
        element={
          <RequireAuth>
            <BalancetePage />
          </RequireAuth>
        }
      />
      <Route
        path="/razao/contas"
        element={
          <RequireAuth>
            <CatalogoContasPage />
          </RequireAuth>
        }
      />
      <Route path="/" element={<Navigate to="/execucao" replace />} />
      <Route path="*" element={<Navigate to="/execucao" replace />} />
    </Routes>
  );
}
