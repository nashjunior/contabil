/**
 * Sessao autenticada — claim gov.br verificada + contexto de ente (ADR-0033 item 3:
 * "estado global so para o que nao e servidor nem formulario": sessao do usuario e
 * tenant/ente ativo, multi-ente ADR-0030).
 *
 * Contrato real (RAZ-101, `ServicoIdentidade`/`SessaoAutenticadaHttpResolver`): o backend
 * aceita `Authorization: Bearer <assercao gov.br>` OU, desde o BFF de login real (ADR-0035,
 * RAZ-128), um cookie de sessão — verificado A CADA request (stateless).
 *
 * HIDRATAÇÃO (RAZ-203/RAZ-205, gap fechado): depois do redirect de volta do gov.br o
 * callback do BFF só estabelece um cookie `HttpOnly` (a asserção nunca chega ao
 * navegador, por desenho — ADR-0035) — este `AuthProvider` chama `GET /sessao/atual`
 * (`sessaoClient.atual`) no mount para aprender `cpfMascarado`/`enteId`/`orgao`/
 * `mfaConcluido` a partir desse cookie. 401 (`sem sessão`) é o resultado normal quando
 * não há cookie/bearer válido — o form de dev (`LoginPage`) segue como único caminho
 * para operar as telas em `VITE_API_MODE=mock` (o backend real ainda não tem entidade
 * `Ente` com nome próprio — RAZ-17 — por isso `enteNome` só existe quando vem do form
 * de dev; a sessão hidratada por cookie não tem nome, só `enteId`, ver `useAuth`
 * consumidores). `expiraEm` NÃO existe no contrato de `SessaoAtualResponse` — a
 * expiração é responsabilidade do cookie/sessão do servidor, o SPA não precisa dela.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { maskCpf } from '../lib/cpf';
import { sessaoClient } from '../api/client';
import type { GovbrContexto } from '../api/client';

export type Sessao = {
  cpfMascarado: string;
  enteId: string;
  /** Só preenchido pelo form de dev (`LoginPage`) — o contrato real ainda não tem
   * entidade `Ente` com nome própria (RAZ-17). Ausente quando a sessão vem hidratada
   * de `GET /sessao/atual`. */
  enteNome?: string;
  orgao: string | null;
  mfaConcluido: boolean;
  /** Ausente quando a sessão veio do cookie do BFF de login (ADR-0035) — nesse caso
   * `shared/api/client.ts` cai no caminho por cookie + CSRF em vez de `Authorization`. */
  bearerToken?: string;
};

type AuthContextValue = {
  sessao: Sessao | null;
  /** true enquanto a hidratação inicial (`GET /sessao/atual`) do mount está em voo. */
  carregando: boolean;
  entrar: (dados: { cpfDigits: string; enteId: string; enteNome: string }) => void;
  sair: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [sessao, setSessao] = useState<Sessao | null>(null);
  const [carregando, setCarregando] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    sessaoClient
      .atual({ signal: controller.signal })
      .then((resposta) => {
        setSessao({
          cpfMascarado: resposta.cpfMascarado,
          enteId: resposta.enteId,
          orgao: resposta.orgao,
          mfaConcluido: resposta.mfaConcluido,
        });
      })
      .catch(() => {
        // 401 nao_autenticado é o resultado normal sem cookie/bearer válido — sessao
        // permanece null e LoginPage/RequireAuth tratam como "não logado".
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregando(false);
      });
    return () => controller.abort();
  }, []);

  const entrar = useCallback((dados: { cpfDigits: string; enteId: string; enteNome: string }) => {
    setSessao({
      cpfMascarado: maskCpf(dados.cpfDigits),
      enteId: dados.enteId,
      enteNome: dados.enteNome,
      orgao: null,
      mfaConcluido: false,
      // DEV ONLY — nao e uma assercao gov.br real (ver comentario do arquivo).
      bearerToken: `dev.${dados.cpfDigits}.${dados.enteId}`,
    });
    setCarregando(false);
  }, []);

  const sair = useCallback(() => setSessao(null), []);

  const value = useMemo(() => ({ sessao, carregando, entrar, sair }), [sessao, carregando, entrar, sair]);

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
