package br.contabil.consulta;

import java.util.Objects;
import java.util.Optional;

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
import br.contabil.sessao.RepositorioAssercaoSessaoLoginGovBr;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

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
 * <p><b>Fallback aditivo por cookie de sessão do BFF de login</b> (ADR-0035 Decisão
 * §2): quando o cabeçalho {@code Authorization} está ausente mas há cookie de sessão
 * BFF válido com asserção guardada ({@link RepositorioAssercaoSessaoLoginGovBr}), lê
 * a asserção e a <b>reverifica via {@code ServicoIdentidade.autenticar} a cada
 * requisição</b> — a mesma invariante stateless acima, nada de autoritativo
 * (identidade/expiração/MFA) é cacheado na sessão HTTP. O caminho por cabeçalho
 * (RAZ-84) permanece inalterado e tem prioridade quando presente.
 *
 * <p>Registrado globalmente por {@link ConsultaWebConfiguration}.
 */
final class SessaoAutenticadaHttpResolver implements HandlerMethodArgumentResolver {

    private static final String CABECALHO_AUTORIZACAO = "Authorization";
    private static final String PREFIXO_BEARER = "Bearer ";

    private final ServicoIdentidade servicoIdentidade;
    private final RepositorioAssercaoSessaoLoginGovBr repositorioAssercaoSessao;

    SessaoAutenticadaHttpResolver(
            ServicoIdentidade servicoIdentidade, RepositorioAssercaoSessaoLoginGovBr repositorioAssercaoSessao) {
        this.servicoIdentidade = Objects.requireNonNull(servicoIdentidade, "servicoIdentidade");
        this.repositorioAssercaoSessao = Objects.requireNonNull(repositorioAssercaoSessao, "repositorioAssercaoSessao");
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
        if (cabecalho != null && !cabecalho.isBlank()) {
            if (!cabecalho.startsWith(PREFIXO_BEARER)) {
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
        Optional<String> assercaoDaSessao = assercaoDoCookieDeSessao(webRequest);
        if (assercaoDaSessao.isPresent()) {
            return servicoIdentidade.autenticar(new CredencialGovBr(assercaoDaSessao.get()));
        }
        throw new NaoAutenticadoException(
                "Cabeçalho 'Authorization: Bearer <asserção gov.br>' ausente ou mal formado, "
                        + "e nenhuma sessão de login (cookie) válida");
    }

    private Optional<String> assercaoDoCookieDeSessao(NativeWebRequest webRequest) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return Optional.empty();
        }
        HttpSession sessaoHttp = request.getSession(false);
        return repositorioAssercaoSessao.assercaoDaSessao(sessaoHttp);
    }
}
