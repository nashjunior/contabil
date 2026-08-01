/**
 * Entrar (RAZ-199): duas seções lado a lado — gov.br REAL (`/sessao/oauth/iniciar`,
 * `SessaoLoginGovBrOAuthController`/ADR-0035) e o stand-in de desenvolvimento (RAZ-120).
 *
 * gov.br real é navegação de página inteira (302 → gov.br → 302 de volta com um cookie
 * HttpOnly, nunca um `fetch`) — só faz sentido contra um backend de fato (`VITE_API_MODE=
 * real`); em `mock` (default deste ambiente) o endpoint não existe para o MSW interceptar,
 * então o link fica desabilitado com a explicação, em vez de quebrar silenciosamente.
 *
 * GAP QUE PERMANECE (não escondido — ver README "Gaps" e AuthContext.tsx): mesmo com o
 * `/iniciar` real wireado, o callback devolve só um cookie de sessão — não há hoje, no
 * backend, um endpoint "quem sou eu" que devolva `{cpf mascarado, ente, orgao}` para este
 * SPA hidratar `AuthContext` depois do redirect de volta. Sem ele, a claim/ente não tem
 * como ser aprendida pelo navegador, então o formulário de dev permanece o único caminho
 * para operar as telas autenticadas neste ambiente — follow-up de backend rastreado
 * separadamente (RAZ-199).
 */
import { useId, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { apiOrigin } from '../api/client';
import { apiMode } from '../api/mode';
import { useAuth } from './AuthContext';

// IDs sao UUID: java TenantId.de(String) faz UUID.fromString(valor) e lanca
// IllegalArgumentException para qualquer coisa que nao seja UUID valido.
const ENTES_DEV = [
  { id: '11111111-1111-4111-8111-111111111111', nome: 'Prefeitura Municipal de Exemplo (ente-a)' },
  { id: '22222222-2222-4222-8222-222222222222', nome: 'Secretaria Estadual de Exemplo (ente-b)' },
];

export function LoginPage() {
  const { entrar } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const cpfId = useId();
  const enteId = useId();
  const erroId = useId();

  const [cpf, setCpf] = useState('');
  const [enteSelecionado, setEnteSelecionado] = useState(ENTES_DEV[0].id);
  const [erro, setErro] = useState<string | null>(null);

  function handleSubmitDev(event: FormEvent<HTMLFormElement>) {
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
      <h1>Entrar</h1>

      <section style={{ marginBottom: 'var(--spacing-xl)' }}>
        <h2>gov.br</h2>
        {apiMode === 'real' ? (
          <a
            href={`${apiOrigin}/sessao/oauth/iniciar`}
            style={{
              display: 'inline-block',
              padding: 'var(--spacing-sm) var(--spacing-lg)',
              borderRadius: 'var(--radius-md)',
              background: 'var(--color-brand-default)',
              color: 'var(--color-text-on-brand)',
              textDecoration: 'none',
              fontWeight: 'var(--typography-weight-medium)',
            }}
          >
            Entrar com gov.br
          </a>
        ) : (
          <p role="note">
            O login gov.br real (<code>/sessao/oauth/iniciar</code>, ADR-0035) exige um backend
            rodando de fato — indisponível em <code>VITE_API_MODE=mock</code> (default deste
            ambiente). Rode <code>VITE_API_MODE=real npm run dev</code> contra um backend real
            para testá-lo.
          </p>
        )}
      </section>

      <section>
        <h2>Modo desenvolvimento</h2>
        <p role="note" style={{ color: 'var(--color-state-warning-fg)' }}>
          <strong>Stand-in de dev, sem curso em produção.</strong> Simula a claim gov.br
          verificada + a escolha de ente para testar as telas contra a API real sem o fluxo
          OAuth completo (o backend real ainda não tem um endpoint para este SPA aprender
          quem é o usuário logado depois do redirect de gov.br — ver comentário no topo deste
          arquivo).
        </p>

        <form onSubmit={handleSubmitDev} noValidate>
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
      </section>
    </main>
  );
}
