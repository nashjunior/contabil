package br.contabil.consulta;

import java.util.Objects;

import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.CredencialGovBr;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.NaoAutenticadoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Resolve {@link Sessao} como parâmetro de qualquer {@code @RestController} de
 * consulta a partir do cabeçalho {@code Authorization: Bearer <asserção gov.br>}
 * (RAZ-101): autentica a asserção via {@link ServicoIdentidade#autenticar} a
 * CADA requisição — stateless, sem cookie/HttpSession, adequado a uma API
 * consultável de fora do processo (RAZ-84). Diferente do fluxo de assinatura
 * (redirect OAuth2 + PKCE, sessão de navegador), que guarda só uma projeção
 * sem PII da sessão numa {@code HttpSession} (RAZ-70,
 * {@code assinatura.SessaoIamAssinaturaHttpSession}) — aqui o próprio
 * {@link ServicoIdentidade} verifica a asserção a cada chamada e devolve a
 * {@link Sessao} completa (com {@code Cpf}) que {@code ControleAcesso.exigir}
 * exige, sem reter nada em memória entre requisições.
 *
 * <p>Registrado globalmente por {@link ConsultaWebConfiguration}.
 */
final class SessaoAutenticadaHttpResolver implements HandlerMethodArgumentResolver {

    private static final String CABECALHO_AUTORIZACAO = "Authorization";
    private static final String PREFIXO_BEARER = "Bearer ";

    private final ServicoIdentidade servicoIdentidade;

    SessaoAutenticadaHttpResolver(ServicoIdentidade servicoIdentidade) {
        this.servicoIdentidade = Objects.requireNonNull(servicoIdentidade, "servicoIdentidade");
    }

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return Sessao.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            @NonNull MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        return autenticar(webRequest);
    }

    Sessao autenticar(NativeWebRequest webRequest) {
        String cabecalho = webRequest.getHeader(CABECALHO_AUTORIZACAO);
        if (cabecalho == null || !cabecalho.startsWith(PREFIXO_BEARER)) {
            throw new NaoAutenticadoException(
                    "Cabeçalho 'Authorization: Bearer <asserção gov.br>' ausente ou mal formado");
        }
        String assercao = cabecalho.substring(PREFIXO_BEARER.length()).trim();
        if (assercao.isEmpty()) {
            throw new NaoAutenticadoException(
                    "Cabeçalho 'Authorization: Bearer <asserção gov.br>' ausente ou mal formado");
        }
        return servicoIdentidade.autenticar(new CredencialGovBr(assercao));
    }
}
