package br.contabil.prestacaocontas.application;

import java.util.List;
import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.prestacaocontas.domain.DimensaoOrganizacionalSimTceCe;
import br.contabil.prestacaocontas.domain.RemessaSimTceCe;
import br.contabil.razao.application.GerarBalancete;
import br.contabil.razao.domain.Balancete;

/** Gera a remessa PGI/ZIP da tabela 308 do SIM/TCE-CE a partir do balancete do razão. */
public class GerarRemessaSimTceCe {

    private static final Recurso RECURSO_REMESSA_SIM = new Recurso("prestacao-contas:sim-tce-ce:remessa");

    private final ControleAcesso controleAcesso;
    private final GerarBalancete gerarBalancete;
    private final RemessaSimTceCePort remessa;

    public GerarRemessaSimTceCe(
            ControleAcesso controleAcesso,
            GerarBalancete gerarBalancete,
            RemessaSimTceCePort remessa) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.gerarBalancete = Objects.requireNonNull(gerarBalancete, "gerarBalancete");
        this.remessa = Objects.requireNonNull(remessa, "remessa");
    }

    public RemessaSimTceCe executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            int exercicio,
            int mes,
            List<DimensaoOrganizacionalSimTceCe> dimensoes) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_REMESSA_SIM, Acao.LER);
        Balancete balancete = gerarBalancete.executar(usuarioAutenticado, enteId, exercicio, mes);
        return remessa.gerarTabela308(balancete, dimensoes);
    }
}
