package br.contabil.assinatura;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.util.WebUtils;

final class SessaoIamAssinaturaHttpSession {

    static final String ATTR_SESSAO_IAM = "siafic.iam.sessao";

    private final Clock clock;

    SessaoIamAssinaturaHttpSession(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    void gravarVerificada(HttpSession sessaoHttp, ServicoIdentidade.Sessao sessaoIam) {
        Objects.requireNonNull(sessaoHttp, "sessaoHttp");
        Objects.requireNonNull(sessaoIam, "sessaoIam");
        exigirSessaoValida(sessaoIam.id(), sessaoIam.mfaConcluido(), sessaoIam.expiraEm());
        SessaoVerificada gravavel = new SessaoVerificada(
                sessaoIam.id(), sessaoIam.ente(), sessaoIam.mfaConcluido(), sessaoIam.expiraEm());
        synchronized (WebUtils.getSessionMutex(sessaoHttp)) {
            sessaoHttp.setAttribute(ATTR_SESSAO_IAM, gravavel);
        }
    }

    SessaoVerificada exigirVerificada(HttpSession sessaoHttp) {
        Objects.requireNonNull(sessaoHttp, "sessaoHttp");
        synchronized (WebUtils.getSessionMutex(sessaoHttp)) {
            Object valor = sessaoHttp.getAttribute(ATTR_SESSAO_IAM);
            if (!(valor instanceof SessaoVerificada sessaoIam)) {
                throw new ServicoIdentidade.NaoAutenticadoException(
                        "Sessao autenticada gov.br/ICP-Brasil ausente; nao iniciar assinatura sem ente verificado");
            }
            try {
                exigirSessaoValida(sessaoIam.id(), sessaoIam.mfaConcluido(), sessaoIam.expiraEm());
            } catch (RuntimeException e) {
                sessaoHttp.removeAttribute(ATTR_SESSAO_IAM);
                throw e;
            }
            return sessaoIam;
        }
    }

    private void exigirSessaoValida(UUID id, boolean mfaConcluido, Instant expiraEm) {
        if (!expiraEm.isAfter(clock.instant())) {
            throw new ServicoIdentidade.NaoAutenticadoException("Sessao IAM gov.br/ICP-Brasil expirada");
        }
        if (!mfaConcluido) {
            throw new ServicoIdentidade.MfaRequeridoException(new ServicoIdentidade.DesafioMfa(id, "assinatura_govbr"));
        }
    }

    // Projecao minima e serializavel de ServicoIdentidade.Sessao: a HttpSession pode ser
    // persistida/replicada (Redis, Tomcat clusterizado), e Sessao/Cpf/Optional nao sao
    // Serializable; tambem evita reter CPF (PII) na sessao web sem necessidade.
    record SessaoVerificada(UUID id, TenantId ente, boolean mfaConcluido, Instant expiraEm) implements Serializable {

        private static final long serialVersionUID = 1L;

        SessaoVerificada {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(ente, "ente");
            Objects.requireNonNull(expiraEm, "expiraEm");
        }
    }
}
