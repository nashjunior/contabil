package br.contabil.sessao;

import java.util.Objects;
import java.util.Optional;

import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Guarda, server-side, a asserção gov.br crua obtida pelo BFF de login (ADR-0035
 * Decisão §1-2) — o navegador nunca a recebe, só um cookie de sessão. Também expõe a
 * leitura usada pelo <i>fallback</i> aditivo do {@code SessaoAutenticadaHttpResolver}
 * (RAZ-84 preservado: caminho por cabeçalho inalterado) e o encerramento de sessão
 * (logout invalida server-side — refinamento de segurança RAZ-127 §2).
 *
 * <p>Nada além da asserção crua é cacheado aqui: quem lê ({@link #assercaoDaSessao})
 * sempre revalida via {@code ServicoIdentidade.autenticar} a cada requisição —
 * expiração/revogação/MFA são sempre autoritativas do gov.br, nunca desta sessão HTTP.
 */
public final class RepositorioAssercaoSessaoLoginGovBr {

    private static final String ATTR_ASSERCAO = "siafic.login.govbr.oauth.assercao";

    /**
     * Grava a asserção após o {@code callback} verificar a sessão IAM com sucesso.
     * Regenera o id da {@code HttpSession} <b>antes</b> de gravar (anti session-fixation,
     * refinamento RAZ-127 §2) — {@link HttpServletRequest#changeSessionId()} preserva os
     * atributos já presentes, só troca o identificador exposto ao navegador.
     */
    public void gravarAposLogin(HttpServletRequest request, String assercao) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(assercao, "assercao");
        if (assercao.isBlank()) {
            throw new IllegalArgumentException("assercao nao pode ser vazia");
        }
        request.changeSessionId();
        HttpSession sessao = request.getSession(true);
        synchronized (WebUtils.getSessionMutex(sessao)) {
            sessao.setAttribute(ATTR_ASSERCAO, assercao);
        }
    }

    /** {@code Optional} vazio quando não há sessão de login (cookie) válida — nunca lança. */
    public Optional<String> assercaoDaSessao(HttpSession sessao) {
        if (sessao == null) {
            return Optional.empty();
        }
        synchronized (WebUtils.getSessionMutex(sessao)) {
            Object valor = sessao.getAttribute(ATTR_ASSERCAO);
            return valor instanceof String assercao && !assercao.isBlank() ? Optional.of(assercao) : Optional.empty();
        }
    }

    /** Logout: invalida a sessão server-side, descartando a asserção guardada (RAZ-127 §2). */
    void encerrar(HttpSession sessao) {
        if (sessao != null) {
            sessao.invalidate();
        }
    }
}
