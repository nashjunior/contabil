package br.contabil.assinatura;

import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.Objects;
import org.springframework.web.util.WebUtils;

final class SessaoIamAssinaturaHttpSession {

    static final String ATTR_SESSAO_IAM = "siafic.iam.sessao";

    private final Clock clock;

    SessaoIamAssinaturaHttpSession(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    void gravarVerificada(HttpSession sessaoHttp, ServicoIdentidade.Sessao sessaoIam) {
        Objects.requireNonNull(sessaoHttp, "sessaoHttp");
        exigirSessaoValida(sessaoIam);
        synchronized (WebUtils.getSessionMutex(sessaoHttp)) {
            sessaoHttp.setAttribute(ATTR_SESSAO_IAM, sessaoIam);
        }
    }

    ServicoIdentidade.Sessao exigirVerificada(HttpSession sessaoHttp) {
        Objects.requireNonNull(sessaoHttp, "sessaoHttp");
        synchronized (WebUtils.getSessionMutex(sessaoHttp)) {
            Object valor = sessaoHttp.getAttribute(ATTR_SESSAO_IAM);
            if (!(valor instanceof ServicoIdentidade.Sessao sessaoIam)) {
                throw new ServicoIdentidade.NaoAutenticadoException(
                        "Sessao autenticada gov.br/ICP-Brasil ausente; nao iniciar assinatura sem ente verificado");
            }
            try {
                exigirSessaoValida(sessaoIam);
            } catch (RuntimeException e) {
                sessaoHttp.removeAttribute(ATTR_SESSAO_IAM);
                throw e;
            }
            return sessaoIam;
        }
    }

    private void exigirSessaoValida(ServicoIdentidade.Sessao sessaoIam) {
        Objects.requireNonNull(sessaoIam, "sessaoIam");
        if (!sessaoIam.expiraEm().isAfter(clock.instant())) {
            throw new ServicoIdentidade.NaoAutenticadoException("Sessao IAM gov.br/ICP-Brasil expirada");
        }
        if (!sessaoIam.mfaConcluido()) {
            throw new ServicoIdentidade.MfaRequeridoException(
                    new ServicoIdentidade.DesafioMfa(sessaoIam.id(), "assinatura_govbr"));
        }
    }
}
