package br.contabil.sessao;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IdP de dev local exposto por HTTP (RAZ-228/ADR-0052 item 1) — substitui a string opaca
 * {@code dev.<cpf>.<ente>} que o formulário "Modo desenvolvimento" de {@code
 * LoginPage.tsx} fabricava (RAZ-120) por um JWT RS256 de verdade, assinado por {@link
 * AssinadorJwtDevIdp} com a chave PRIVADA de dev (só existe neste processo — nunca no
 * bundle do navegador, ver "Alternativas consideradas" do ADR-0052). O restante do
 * caminho não muda: o SPA guarda {@code bearerToken} e {@code SessaoAutenticadaHttpResolver}
 * (RAZ-84) reverifica QUALQUER bearer via {@code ServicoIdentidade}, real ou de dev, do
 * mesmo jeito. Wiring de frontend (chamar este endpoint em vez de fabricar a string) é
 * issue-filha separada de frontend — fora da fronteira deste endpoint.
 *
 * <p>{@code papel} do request é aceito só para o contrato ficar estável com o form de
 * dev do SPA — NÃO vira claim do JWT nem decide nada aqui: RBAC é resolvido inteiramente
 * por {@code siafic.iam.concessoes} (deny-by-default), o dev precisa ter provisionado a
 * concessão correspondente para o CPF/ente escolhidos, exatamente como valeria em
 * produção.
 *
 * <p>Rota condicional (ver {@link ConditionalOnDevIdpAtivo}/{@link
 * SessaoDevIdpConfiguration}) — sem as duas flags de guarda, este controller nem é um
 * bean: {@code POST /sessao/dev-idp/token} devolve 404, nunca 403/500 — fail-closed por
 * ausência da rota, não por uma checagem em runtime que alguém possa esquecer de chamar.
 */
@RestController
@RequestMapping("/sessao/dev-idp")
@ConditionalOnDevIdpAtivo
final class SessaoDevIdpController {

    private final AssinadorJwtDevIdp assinador;

    SessaoDevIdpController(AssinadorJwtDevIdp assinador) {
        this.assinador = Objects.requireNonNull(assinador, "assinador");
    }

    @PostMapping("/token")
    ResponseEntity<?> emitirToken(@RequestBody SessaoDevIdpRequest requisicao) {
        String cpf = cpfNormalizado(requisicao == null ? null : requisicao.cpf());
        if (cpf == null) {
            return ResponseEntity.badRequest().body(Map.of("erro", "cpf_invalido"));
        }
        UUID enteId;
        try {
            enteId = UUID.fromString(requisicao.enteId());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", "ente_id_invalido"));
        }
        return ResponseEntity.ok(new SessaoDevIdpResponse(assinador.assinar(cpf, enteId)));
    }

    private static String cpfNormalizado(String cpf) {
        if (cpf == null) {
            return null;
        }
        String digitos = cpf.replaceAll("\\D", "");
        return digitos.length() == 11 ? digitos : null;
    }

    record SessaoDevIdpRequest(String cpf, String enteId, String papel) {}

    record SessaoDevIdpResponse(String bearerToken) {}
}
