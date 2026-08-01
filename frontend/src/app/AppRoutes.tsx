import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from '../shared/auth/LoginPage';
import { RequireAuth } from '../shared/auth/RequireAuth';
import { AprovacaoFilaPage, ExecucaoPage, LiquidacaoPage, PagamentoPage, TrilhaLiquidacaoPage } from '../features/execucao';
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
        path="/execucao/liquidacoes"
        element={
          <RequireAuth>
            <LiquidacaoPage />
          </RequireAuth>
        }
      />
      <Route
        path="/execucao/liquidacoes/:id/trilha"
        element={
          <RequireAuth>
            <TrilhaLiquidacaoPage />
          </RequireAuth>
        }
      />
      <Route
        path="/execucao/pagamentos"
        element={
          <RequireAuth>
            <PagamentoPage />
          </RequireAuth>
        }
      />
      <Route
        path="/execucao/aprovacoes"
        element={
          <RequireAuth>
            <AprovacaoFilaPage />
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
