import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App.tsx';
import { apiMode } from './shared/api/mode';

async function enableMocksSeNecessario() {
  if (apiMode !== 'mock') return;
  const { worker } = await import('./shared/api/mocks/browser');
  await worker.start({ onUnhandledRequest: 'bypass' });
}

enableMocksSeNecessario().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});
