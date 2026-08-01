package br.contabil.sessao;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * "Quem sou eu" (RAZ-203/ADR-0035): {@code GET /sessao/atual} — o SPA hidrata seu
 * {@code AuthContext} com isto depois do redirect de volta do BFF de login
 * ({@link SessaoLoginGovBrOAuthController#callback}), que só estabelece um cookie
 * {@code HttpOnly} (a asserção gov.br nunca chega ao navegador, por desenho —
 * ADR-0035). Sem lógica própria: só reflete os campos já verificados da
 * {@link Sessao} resolvida por {@code SessaoAutenticadaHttpResolver} (cookie OU
 * {@code Authorization: Bearer}, RAZ-84/RAZ-128) — {@code 401 nao_autenticado}
 * (mesmo envelope do {@code ErroContratoExceptionHandler}) quando nenhuma das duas
 * autentica.
 *
 * <p>Deliberadamente <b>sem</b> {@code ControleAcesso.exigir}/RBAC: nenhum
 * {@code Papel} operacional de {@code IamProperties} (RAZ-33) concede
 * {@code Acao.LER} genérico — só {@code AUDITOR}. Gatear "quem sou eu" atrás de
 * autorização travaria fora da própria sessão que acabou de abrir todo usuário
 * recém-logado sem o papel AUDITOR (LANCADOR/AUTORIZADOR/PAGADOR inclusive). O
 * único portão é autenticação — a mesma que qualquer consulta já exige, sem MFA
 * (RAZ-33: {@code Acao.LER} nunca exige) — e a resposta nunca expõe nada que a
 * {@link Sessao} já verificada não carregue.
 *
 * <p>{@code enteId} devolve só o id — o domínio ainda não tem a entidade
 * {@code Ente} com nome própria (RAZ-17, ver {@code TenantId}); quando ela
 * chegar, este contrato ganha {@code enteNome} junto.
 */
@RestController
@RequestMapping("/sessao")
final class SessaoAtualController {

    @GetMapping("/atual")
    SessaoAtualResponse atual(Sessao sessao) {
        return SessaoAtualResponse.de(sessao);
    }

    record SessaoAtualResponse(String cpfMascarado, UUID enteId, String orgao, boolean mfaConcluido) {
        static SessaoAtualResponse de(Sessao sessao) {
            return new SessaoAtualResponse(
                    sessao.titular().mascarado(),
                    sessao.ente().valor(),
                    sessao.orgao().orElse(null),
                    sessao.mfaConcluido());
        }
    }
}
