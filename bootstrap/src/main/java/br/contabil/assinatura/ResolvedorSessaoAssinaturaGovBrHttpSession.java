package br.contabil.assinatura;

import br.contabil.plataforma.domain.TenantId;
import jakarta.servlet.http.HttpSession;
import java.util.Objects;

final class ResolvedorSessaoAssinaturaGovBrHttpSession implements ResolvedorSessaoAssinaturaGovBr {

    private final SessaoIamAssinaturaHttpSession sessoesIam;

    ResolvedorSessaoAssinaturaGovBrHttpSession(SessaoIamAssinaturaHttpSession sessoesIam) {
        this.sessoesIam = Objects.requireNonNull(sessoesIam, "sessoesIam");
    }

    @Override
    public TenantId enteAutenticado(HttpSession sessao) {
        Objects.requireNonNull(sessao, "sessao");
        return sessoesIam.exigirVerificada(sessao).ente();
    }
}
