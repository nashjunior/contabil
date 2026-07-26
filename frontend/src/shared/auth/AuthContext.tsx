/**
 * Sessao autenticada — claim gov.br verificada + contexto de ente (ADR-0033 item 3:
 * "estado global so para o que nao e servidor nem formulario": sessao do usuario e
 * tenant/ente ativo, multi-ente ADR-0030).
 *
 * Contrato real (RAZ-101, `ServicoIdentidade`/`SessaoAutenticadaHttpResolver`): o backend
 * espera `Authorization: Bearer <assercao gov.br>`, verificada A CADA request (stateless) —
 * `ServicoIdentidade.autenticar(new CredencialGovBr(assercao))` devolve uma `Sessao` com
 * `titular` (CPF), `ente`, `orgao`, `mfaConcluido`, `expiraEm`.
 *
 * GAP (RAZ-120, não escondido): não há, neste ambiente, um jeito de OBTER uma asserção
 * gov.br real (isso depende de registro de cliente OAuth2/OIDC junto ao gov.br — processo
 * externo, com credenciais de governo, análogo ao runbook RAZ-39 mas para login geral, não
 * só assinatura). Este `AuthProvider` é um STAND-IN DE DESENVOLVIMENTO: sintetiza um bearer
 * token opaco local (nunca teria curso em produção) para permitir montar e testar a tela
 * contra o contrato real. Trocar por um fluxo OIDC real é follow-up (dono: backend/Aurélio +
 * segurança/Solange) — ver DevLoginPage e README "Gaps".
 */
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { maskCpf } from '../lib/cpf';
import type { GovbrContexto } from '../api/client';

export type Sessao = {
  cpfDigits: string;
  cpfMascarado: string;
  enteId: string;
  enteNome: string;
  bearerToken: string;
};

type AuthContextValue = {
  sessao: Sessao | null;
  entrar: (dados: { cpfDigits: string; enteId: string; enteNome: string }) => void;
  sair: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [sessao, setSessao] = useState<Sessao | null>(null);

  const entrar = useCallback((dados: { cpfDigits: string; enteId: string; enteNome: string }) => {
    setSessao({
      cpfDigits: dados.cpfDigits,
      cpfMascarado: maskCpf(dados.cpfDigits),
      enteId: dados.enteId,
      enteNome: dados.enteNome,
      // DEV ONLY — nao e uma assercao gov.br real (ver comentario do arquivo).
      bearerToken: `dev.${dados.cpfDigits}.${dados.enteId}`,
    });
  }, []);

  const sair = useCallback(() => setSessao(null), []);

  const value = useMemo(() => ({ sessao, entrar, sair }), [sessao, entrar, sair]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth deve ser usado dentro de <AuthProvider>');
  return ctx;
}

/** Deriva o contexto gov.br exigido pelo client de API a partir da sessao ativa. */
export function useGovbrContexto(): GovbrContexto {
  const { sessao } = useAuth();
  if (!sessao) throw new Error('useGovbrContexto chamado sem sessao ativa — proteger a rota com <RequireAuth>.');
  return { bearerToken: sessao.bearerToken, enteId: sessao.enteId };
}
