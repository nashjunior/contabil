import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from '../shared/auth/LoginPage';
import { RequireAuth } from '../shared/auth/RequireAuth';
import { AprovacaoFilaPage, ExecucaoPage, LiquidacaoPage, PagamentoPage, TrilhaLiquidacaoPage } from '../features/execucao';
import { BalancetePage, CatalogoContasPage, SaldoContaPage } from '../features/razao';
import {
  DicionarioDadosPage,
  ListaTotaisPage,
  TransparenciaBulkPage,
  TransparenciaDetalhePage,
} from '../features/transparencia';

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
      {/* Portal público de transparência (RAZ-290/ADR-0060) — SEM `RequireAuth`: superfície
          pública, sem sessão, o ente vem do path (mesmo contrato do back-office real, mas
          shell/rota deliberadamente separados — ver `features/transparencia/components/
          PortalLayout.tsx`). */}
      <Route path="/transparencia/:enteId" element={<ListaTotaisPage />} />
      <Route path="/transparencia/:enteId/despesas/:sequencia" element={<TransparenciaDetalhePage />} />
      <Route path="/transparencia/:enteId/dicionario-dados" element={<DicionarioDadosPage />} />
      <Route path="/transparencia/:enteId/bulk" element={<TransparenciaBulkPage />} />

      <Route path="/" element={<Navigate to="/execucao" replace />} />
      <Route path="*" element={<Navigate to="/execucao" replace />} />
    </Routes>
  );
}
