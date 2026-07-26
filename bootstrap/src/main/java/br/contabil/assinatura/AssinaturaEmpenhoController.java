package br.contabil.assinatura;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.contabil.execucao.application.AssinarEmpenho;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.CredencialGovBr;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import jakarta.servlet.http.HttpSession;

/**
 * {@code POST /entes/{enteId}/execucao/empenhos/{id}/assinatura} (ADR-0027
 * (c)/RAZ-103): dispara {@link AssinarEmpenho} na própria sessão OAuth2 gov.br
 * interativa do ordenador já autenticada em {@code AssinaturaGovBrOAuthController}
 * (RAZ-28/29) — não a sessão stateless de {@code SessaoAutenticadaHttpResolver}
 * (RAZ-101), que reautentica por header a cada chamada e nunca guarda o token de
 * assinatura na {@code HttpSession}.
 *
 * <p>Reautentica a MESMA credencial gov.br já trocada no callback OAuth2 (o
 * token continua em {@code RepositorioSessaoAssinaturaGovBr}) para reconstruir
 * a {@link Sessao} completa (com {@code Cpf}) que {@code ControleAcesso.exigir}
 * exige — a projeção guardada na {@code HttpSession} ({@code
 * SessaoIamAssinaturaHttpSession.SessaoVerificada}) deliberadamente não retém
 * CPF (minimização de PII).
 */
@RestController
@RequestMapping("/entes/{enteId}/execucao/empenhos")
final class AssinaturaEmpenhoController {

    private final ObjectProvider<AssinarEmpenho> assinarEmpenho;
    private final RepositorioSessaoAssinaturaGovBr repositorioToken;
    private final SessaoIamAssinaturaHttpSession sessoesIam;
    private final ServicoIdentidade servicoIdentidade;

    AssinaturaEmpenhoController(
            ObjectProvider<AssinarEmpenho> assinarEmpenho,
            RepositorioSessaoAssinaturaGovBr repositorioToken,
            SessaoIamAssinaturaHttpSession sessoesIam,
            ServicoIdentidade servicoIdentidade) {
        this.assinarEmpenho = Objects.requireNonNull(assinarEmpenho, "assinarEmpenho");
        this.repositorioToken = Objects.requireNonNull(repositorioToken, "repositorioToken");
        this.sessoesIam = Objects.requireNonNull(sessoesIam, "sessoesIam");
        this.servicoIdentidade = Objects.requireNonNull(servicoIdentidade, "servicoIdentidade");
    }

    @PostMapping("/{id}/assinatura")
    ResponseEntity<?> assinar(@PathVariable("enteId") UUID enteId, @PathVariable("id") UUID id, HttpSession sessaoHttp) {
        AssinarEmpenho useCase = assinarEmpenho.getIfAvailable();
        if (useCase == null) {
            throw new IllegalStateException(
                    "assinatura eletrônica gov.br não configurada (siafic.assinatura.govbr.enabled)");
        }

        SessaoIamAssinaturaHttpSession.SessaoVerificada sessaoVerificada = sessoesIam.exigirVerificada(sessaoHttp);
        if (!sessaoVerificada.ente().valor().equals(enteId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("erro", "ente_divergente"));
        }
        String token = repositorioToken.tokenAtual(sessaoHttp);
        Sessao sessao = servicoIdentidade.autenticar(new CredencialGovBr(token));
        if (!sessao.ente().valor().equals(enteId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("erro", "ente_divergente"));
        }

        Empenho empenho = useCase.executar(sessao, new TenantId(enteId), new EmpenhoId(id));
        return ResponseEntity.ok(AssinaturaEmpenhoResponse.de(empenho));
    }
}
