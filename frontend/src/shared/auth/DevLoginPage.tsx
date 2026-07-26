/**
 * Login DEV — NAO e integracao gov.br real (gap documentado em AuthContext.tsx e
 * no RAZ-120). Simula a claim verificada + escolha de ente para permitir construir
 * e testar a tela real contra o contrato provisorio. Rotulado explicitamente na UI.
 */
import { useId, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from './AuthContext';

// IDs sao UUID: java TenantId.de(String) faz UUID.fromString(valor) e lanca
// IllegalArgumentException para qualquer coisa que nao seja UUID valido.
const ENTES_DEV = [
  { id: '11111111-1111-4111-8111-111111111111', nome: 'Prefeitura Municipal de Exemplo (ente-a)' },
  { id: '22222222-2222-4222-8222-222222222222', nome: 'Secretaria Estadual de Exemplo (ente-b)' },
];

export function DevLoginPage() {
  const { entrar } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const cpfId = useId();
  const enteId = useId();
  const erroId = useId();

  const [cpf, setCpf] = useState('');
  const [enteSelecionado, setEnteSelecionado] = useState(ENTES_DEV[0].id);
  const [erro, setErro] = useState<string | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const digits = cpf.replace(/\D/g, '');
    if (digits.length !== 11) {
      setErro('Informe um CPF com 11 dígitos (dev: qualquer sequência de 11 dígitos serve).');
      return;
    }
    const ente = ENTES_DEV.find((e) => e.id === enteSelecionado);
    if (!ente) {
      setErro('Selecione um ente.');
      return;
    }
    setErro(null);
    entrar({ cpfDigits: digits, enteId: ente.id, enteNome: ente.nome });
    const destino = (location.state as { from?: Location })?.from?.pathname ?? '/execucao';
    navigate(destino, { replace: true });
  }

  return (
    <main style={{ maxWidth: 420, margin: '0 auto', padding: 'var(--spacing-xl)' }}>
      <h1>Entrar (gov.br)</h1>
      <p role="note" style={{ color: 'var(--color-state-warning-fg)' }}>
        <strong>Modo desenvolvimento.</strong> Não há, hoje, integração real de login
        gov.br no backend — este formulário simula a claim verificada para permitir
        testar a tela contra a API real. Ver gap documentado no RAZ-120.
      </p>

      <form onSubmit={handleSubmit} noValidate>
        <div style={{ marginBottom: 'var(--spacing-lg)' }}>
          <label htmlFor={cpfId}>CPF</label>
          <input
            id={cpfId}
            name="cpf"
            inputMode="numeric"
            autoComplete="off"
            value={cpf}
            onChange={(e) => setCpf(e.target.value)}
            aria-describedby={erro ? erroId : undefined}
            aria-invalid={erro ? true : undefined}
          />
        </div>

        <div style={{ marginBottom: 'var(--spacing-lg)' }}>
          <label htmlFor={enteId}>Ente</label>
          <select id={enteId} name="ente" value={enteSelecionado} onChange={(e) => setEnteSelecionado(e.target.value)}>
            {ENTES_DEV.map((ente) => (
              <option key={ente.id} value={ente.id}>
                {ente.nome}
              </option>
            ))}
          </select>
        </div>

        {erro && (
          <p id={erroId} role="alert" style={{ color: 'var(--color-state-danger-fg)' }}>
            {erro}
          </p>
        )}

        <button type="submit">Entrar</button>
      </form>
    </main>
  );
}
