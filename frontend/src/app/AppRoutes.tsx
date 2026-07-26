import { Navigate, Route, Routes } from 'react-router-dom';
import { DevLoginPage } from '../shared/auth/DevLoginPage';
import { RequireAuth } from '../shared/auth/RequireAuth';
import { ExecucaoPage } from '../features/execucao';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/entrar" element={<DevLoginPage />} />
      <Route
        path="/execucao"
        element={
          <RequireAuth>
            <ExecucaoPage />
          </RequireAuth>
        }
      />
      <Route path="/" element={<Navigate to="/execucao" replace />} />
      <Route path="*" element={<Navigate to="/execucao" replace />} />
    </Routes>
  );
}
