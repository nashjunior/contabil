package br.contabil.plataforma.domain.iam;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import br.contabil.plataforma.domain.TenantId;

/**
 * Ponto único onde um caso de uso protegido pede permissão antes de agir
 * (RAZ-33, piso F0 — docs/13-nfr-e-operacao.md §Piso de segurança F0;
 * docs/transversais/04-lgpd.md §RBAC/MFA). Compõe {@link ServicoIdentidade} com
 * duas regras que o contrato sozinho não expressa:
 *
 * <ol>
 *   <li>o tenant da requisição precisa bater com o tenant da sessão verificada
 *       — nunca confiar num {@link TenantId} que discorda da claim
 *       (guardiao-seguranca regra 2, anti-BOLA);
 *   <li>ações que <b>movimentam recurso</b> (toda {@link ServicoIdentidade.Acao}
 *       além de {@code LER}) exigem MFA concluído na sessão.
 * </ol>
 */
public final class ControleAcesso {

    private static final Set<ServicoIdentidade.Acao> MOVIMENTA_RECURSO =
            EnumSet.complementOf(EnumSet.of(ServicoIdentidade.Acao.LER));

    private final ServicoIdentidade servicoIdentidade;

    public ControleAcesso(ServicoIdentidade servicoIdentidade) {
        this.servicoIdentidade = Objects.requireNonNull(servicoIdentidade, "servicoIdentidade");
    }

    /**
     * Autoriza {@code usuarioAutenticado} a executar {@code acao} sobre
     * {@code objetoProtegido} no tenant {@code enteDaRequisicao} — ou estoura.
     * Ordem: tenant (mais barato, mais crítico) → RBAC → MFA (só cobrado de
     * quem já teria permissão, para não sinalizar MFA a quem seria negado de
     * qualquer forma).
     *
     * @throws ServicoIdentidade.SemPermissaoException tenant da requisição não bate com o da sessão
     *     autenticada, ou a ação foi negada pelo RBAC (deny-by-default)
     * @throws ServicoIdentidade.MfaRequeridoException a ação movimenta recurso e a sessão não concluiu MFA
     */
    public void exigir(
            ServicoIdentidade.Sessao usuarioAutenticado,
            TenantId enteDaRequisicao,
            ServicoIdentidade.Recurso objetoProtegido,
            ServicoIdentidade.Acao acao) {
        Objects.requireNonNull(usuarioAutenticado, "usuarioAutenticado");
        Objects.requireNonNull(enteDaRequisicao, "enteDaRequisicao");
        Objects.requireNonNull(objetoProtegido, "objetoProtegido");
        Objects.requireNonNull(acao, "acao");

        if (!usuarioAutenticado.ente().equals(enteDaRequisicao)) {
            throw new ServicoIdentidade.SemPermissaoException(
                    "tenant da requisição (%s) não corresponde ao tenant da sessão autenticada (%s)"
                            .formatted(enteDaRequisicao, usuarioAutenticado.ente()));
        }
        if (!servicoIdentidade.autorizar(usuarioAutenticado, objetoProtegido, acao)) {
            throw new ServicoIdentidade.SemPermissaoException(
                    "ação %s negada sobre %s".formatted(acao, objetoProtegido.urn()));
        }
        if (MOVIMENTA_RECURSO.contains(acao) && !usuarioAutenticado.mfaConcluido()) {
            throw new ServicoIdentidade.MfaRequeridoException(
                    new ServicoIdentidade.DesafioMfa(usuarioAutenticado.id(), "acao_movimenta_recurso"));
        }
    }
}
