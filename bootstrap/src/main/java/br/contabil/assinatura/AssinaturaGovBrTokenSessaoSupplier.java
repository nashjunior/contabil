package br.contabil.assinatura;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

final class AssinaturaGovBrTokenSessaoSupplier implements Supplier<String> {

    private final RepositorioSessaoAssinaturaGovBr repositorio;

    AssinaturaGovBrTokenSessaoSupplier(RepositorioSessaoAssinaturaGovBr repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
    }

    @Override
    public String get() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
            throw new IllegalStateException("Token OAuth2 gov.br de assinatura exige requisicao HTTP corrente");
        }
        HttpServletRequest request = atributos.getRequest();
        HttpSession sessao = request.getSession(false);
        if (sessao == null) {
            throw new IllegalStateException("Sessao HTTP ausente para token OAuth2 gov.br de assinatura");
        }
        return repositorio.tokenAtual(sessao);
    }
}
