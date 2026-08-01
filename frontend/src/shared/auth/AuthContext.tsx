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
 *
 * `entrar` (RAZ-242, follow-up de frontend da RAZ-228/ADR-0052 item 1): o form de dev
 * (`LoginPage`) não fabrica mais um bearer opaco — chama `sessaoClient.emitirTokenDev`
 * (`POST /sessao/dev-idp/token`) e usa o JWT RS256 real devolvido. `entrar` virou async por
 * causa desse round-trip; erros (`DevIdpTokenError`) propagam pro chamador (`LoginPage`) tratar.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { maskCpf } from '../lib/cpf';
import { registrarObservadorSessaoExpirada, sessaoClient } from '../api/client';
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
  /**
   * true quando a sessão terminou porque uma chamada autenticada devolveu 401 DEPOIS de já
   * ter havido sessão (expirou/foi revogada no meio do uso), distinto de "nunca logou" — RAZ-239.
   * `RequireAuth` traduz isso no `motivo` do redirect; `entrar`/`sair` zeram. Um logout
   * explícito (`sair`) não é expiração.
   */
  sessaoExpirada: boolean;
  /** Lança `DevIdpTokenError` (ou o erro de rede) se `POST /sessao/dev-idp/token` falhar —
   * a sessão só é gravada em caso de sucesso. */
  entrar: (dados: { cpfDigits: string; enteId: string; enteNome: string }) => Promise<void>;
  sair: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [sessao, setSessao] = useState<Sessao | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [sessaoExpirada, setSessaoExpirada] = useState(false);

  // Reflete a sessão atual para o observador de 401 (assíncrono) ler sem recriar o callback
  // registrado — só marca expiração se HAVIA sessão (senão um 401 tardio de "nunca logou"
  // viraria falso "expirou").
  const sessaoRef = useRef<Sessao | null>(null);
  useEffect(() => {
    sessaoRef.current = sessao;
  }, [sessao]);

  // 401 numa chamada AUTENTICADA pós-hidratação (`get`/`post`, nunca `sessaoClient.atual`) =
  // sessão caiu no meio do uso: derruba a sessão e marca o motivo para `RequireAuth`/`LoginPage`.
  useEffect(() => {
    registrarObservadorSessaoExpirada(() => {
      if (!sessaoRef.current) return;
      sessaoRef.current = null; // evita re-marcar por 401s concorrentes da mesma queda
      setSessaoExpirada(true);
      setSessao(null);
    });
    return () => registrarObservadorSessaoExpirada(null);
  }, []);

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

  const entrar = useCallback(async (dados: { cpfDigits: string; enteId: string; enteNome: string }) => {
    const { bearerToken } = await sessaoClient.emitirTokenDev({ cpf: dados.cpfDigits, enteId: dados.enteId });
    setSessaoExpirada(false);
    setSessao({
      cpfMascarado: maskCpf(dados.cpfDigits),
      enteId: dados.enteId,
      enteNome: dados.enteNome,
      orgao: null,
      mfaConcluido: false,
      bearerToken,
    });
    setCarregando(false);
  }, []);

  // Logout explícito ≠ expiração: zera o motivo para não mostrar "sessão expirou" a quem saiu.
  const sair = useCallback(() => {
    setSessaoExpirada(false);
    setSessao(null);
  }, []);

  const value = useMemo(
    () => ({ sessao, carregando, sessaoExpirada, entrar, sair }),
    [sessao, carregando, sessaoExpirada, entrar, sair],
  );

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
