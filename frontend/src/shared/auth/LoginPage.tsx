/**
 * Entrar (RAZ-199): duas seções lado a lado — gov.br REAL (`/sessao/oauth/iniciar`,
 * `SessaoLoginGovBrOAuthController`/ADR-0035) e o stand-in de desenvolvimento (RAZ-120).
 *
 * gov.br real é navegação de página inteira (302 → gov.br → 302 de volta com um cookie
 * HttpOnly, nunca um `fetch`) — só faz sentido contra um backend de fato (`VITE_API_MODE=
 * real`); em `mock` (default deste ambiente) o endpoint não existe para o MSW interceptar,
 * então o link fica desabilitado com a explicação, em vez de quebrar silenciosamente.
 *
 * (Fechado pela RAZ-203/RAZ-205 — não mais um gap): o callback do BFF continua devolvendo
 * só um cookie de sessão, mas agora `GET /sessao/atual` existe no backend e `AuthProvider`
 * já chama esse endpoint no mount para hidratar `{cpfMascarado, enteId, orgao,
 * mfaConcluido}` a partir dele (ver comentário no topo de `AuthContext.tsx`). O formulário
 * de dev abaixo continua existindo por outro motivo: em `VITE_API_MODE=mock` não há gov.br
 * real pra gerar o cookie em primeiro lugar (o link fica desabilitado acima), então ele
 * segue sendo o único caminho para operar as telas autenticadas *neste ambiente*.
 *
 * (Fechado pela RAZ-242/ADR-0052 item 1 — não mais um gap): o formulário de dev não fabrica
 * mais a string opaca `dev.<cpf>.<ente>` — `AuthContext.entrar` chama `POST
 * /sessao/dev-idp/token` (RAZ-228) e usa o JWT RS256 real devolvido. `papel` não é enviado: o
 * contrato do endpoint o aceita só para o form ficar estável, mas o backend nunca o lê (RBAC é
 * 100% via `siafic.iam.concessoes` server-side) — este form não ganhou um seletor de papel.
 */
import { useId, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Alert } from '@siafic/design-system';
import { apiOrigin, DevIdpTokenError } from '../api/client';
import { apiMode } from '../api/mode';
import { useAuth } from './AuthContext';
import { MOTIVO_SESSAO_EXPIRADA, type EstadoRedirecionamentoLogin } from './redirecionamentoLogin';

// IDs sao UUID: java TenantId.de(String) faz UUID.fromString(valor) e lanca
// IllegalArgumentException para qualquer coisa que nao seja UUID valido.
const ENTES_DEV = [
  { id: '11111111-1111-4111-8111-111111111111', nome: 'Prefeitura Municipal de Exemplo (ente-a)' },
  { id: '22222222-2222-4222-8222-222222222222', nome: 'Secretaria Estadual de Exemplo (ente-b)' },
];

// cpf_invalido/ente_id_invalido: o client já valida CPF (11 dígitos)/ente (dropdown fechado)
// antes de chamar o endpoint — só chegam via alguma inconsistência fora do fluxo normal do
// form. endpoint_indisponivel (404 sintético, ver DevIdpTokenError): backend real rodando sem
// as flags de dev-IdP (SIAFIC_DEV_IDP_ENABLED/IAM_ENABLED) — esperado em produção/staging, não
// deveria acontecer em VITE_API_MODE=mock (MSW intercepta antes da rede).
const MENSAGEM_POR_ERRO_DEV_IDP: Record<string, string> = {
  cpf_invalido: 'Informe um CPF com 11 dígitos.',
  ente_id_invalido: 'Selecione um ente válido.',
  endpoint_indisponivel:
    'Este modo de desenvolvimento não está disponível neste backend (dev-IdP desligado). Use o login gov.br ou peça para habilitar SIAFIC_DEV_IDP_ENABLED/IAM_ENABLED.',
};

function mensagemErroDevIdp(erro: unknown): string {
  if (erro instanceof DevIdpTokenError) {
    return MENSAGEM_POR_ERRO_DEV_IDP[erro.erro] ?? 'Não foi possível emitir o token de desenvolvimento. Tente novamente.';
  }
  return 'Não foi possível entrar. Verifique sua conexão e tente novamente.';
}

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
  const [entrando, setEntrando] = useState(false);

  // Motivo é navegação-específico (anexado por `RequireAuth` só no redirect por expiração),
  // não estado global: uma visita direta a `/entrar` não carrega `state` e cai no form simples.
  const estado = location.state as EstadoRedirecionamentoLogin | null;
  const sessaoExpirada = estado?.motivo === MOTIVO_SESSAO_EXPIRADA;

  async function handleSubmitDev(event: FormEvent<HTMLFormElement>) {
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
    setEntrando(true);
    try {
      await entrar({ cpfDigits: digits, enteId: ente.id, enteNome: ente.nome });
      const destino = estado?.from?.pathname ?? '/execucao';
      navigate(destino, { replace: true });
    } catch (erroEntrar) {
      setErro(mensagemErroDevIdp(erroEntrar));
      setEntrando(false);
    }
  }

  return (
    <main style={{ maxWidth: 420, margin: '0 auto', padding: 'var(--spacing-xl)' }}>
      <h1>Entrar</h1>

      {sessaoExpirada && (
        <div style={{ marginBottom: 'var(--spacing-lg)' }}>
          <Alert level="warning">
            <Alert.Title>Sessão expirada</Alert.Title>
            <Alert.Body>Sua sessão expirou. Entre novamente para continuar.</Alert.Body>
          </Alert>
        </div>
      )}

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
          verificada + a escolha de ente para testar as telas sem o fluxo OAuth completo. O
          backend já aprende quem está logado via <code>GET /sessao/atual</code> (RAZ-203/205);
          o bearer vem de <code>POST /sessao/dev-idp/token</code> (RAZ-228/242), um JWT RS256
          real assinado pelo backend — só existe quando o backend roda com as flags de dev-IdP
          ativas (ver README do backend), então em <code>VITE_API_MODE=real</code> contra um
          backend sem essas flags este modo devolve 404.
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

          <button type="submit" disabled={entrando}>
            {entrando ? 'Entrando…' : 'Entrar'}
          </button>
        </form>
      </section>
    </main>
  );
}
