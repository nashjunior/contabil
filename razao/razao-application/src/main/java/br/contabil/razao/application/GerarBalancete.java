package br.contabil.razao.application;

import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.Balancete;
import br.contabil.razao.domain.repository.BalancetePort;

/**
 * Caso de uso: gera o balancete (de encerramento ou comparativo entre períodos)
 * de um ente — lista de contas com saldo anterior/movimento D/movimento
 * C/saldo atual (razao-contabil-schema.md §Saldos, RAZ-97). Mesmo padrão de
 * {@link ConsultarSaldo}: {@code executar(..)} cai no advisor de tenant/transação
 * de {@code TenantContextUseCasesConfiguration} e ganha {@link ControleAcesso}
 * de graça. {@link Acao#LER} nunca exige MFA (RAZ-33).
 */
public class GerarBalancete {

    private static final Recurso RECURSO_BALANCETE = new Recurso("razao:balancete");

    private final ControleAcesso controleAcesso;
    private final BalancetePort balancete;

    public GerarBalancete(ControleAcesso controleAcesso, BalancetePort balancete) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.balancete = Objects.requireNonNull(balancete, "balancete");
    }

    public Balancete executar(Sessao usuarioAutenticado, TenantId enteId, int exercicio, int mes) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_BALANCETE, Acao.LER);
        return balancete.balancete(enteId, exercicio, mes);
    }
}
