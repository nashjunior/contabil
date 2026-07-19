package br.contabil.plataforma.infra.iam;

import br.contabil.plataforma.domain.iam.ServicoIdentidade;

/**
 * Placeholder F0: nenhum provedor gov.br/ICP-Brasil real está plugado ainda —
 * o adapter de verdade (validação de claim gov.br OIDC / cadeia ICP-Brasil) é
 * entregue à parte (RAZ-5, ADR-0014, ver também {@code TenantContext} javadoc).
 *
 * <p>Existe para o contexto Spring poder subir com um {@link ServicoIdentidade}
 * de verdade — em vez de simplesmente não declarar bean nenhum, o que só
 * quebraria tarde, no primeiro {@code @Autowired} que precisasse dele — mas
 * fail-closed em toda operação: nunca autentica, nunca autoriza. RAZ-5
 * substitui este bean por um adapter real; até lá, qualquer caso de uso
 * protegido por {@link br.contabil.plataforma.domain.iam.ControleAcesso} fica
 * bloqueado por design, nunca aberto por omissão.
 */
public final class ServicoIdentidadeIndisponivel implements ServicoIdentidade {

    private static final String MOTIVO =
            "nenhum provedor de identidade gov.br/ICP-Brasil plugado ainda (RAZ-5) — fail-closed";

    @Override
    public Sessao autenticar(Credencial credencial) {
        throw new NaoAutenticadoException(MOTIVO);
    }

    @Override
    public boolean autorizar(Sessao sessao, Recurso recurso, Acao acao) {
        return false;
    }

    @Override
    public Sessao completarMfa(DesafioMfa desafio, RespostaMfa resposta) {
        throw new NaoAutenticadoException(MOTIVO);
    }
}
