package br.contabil.razao.application;

import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.DemonstracoesDcasp;
import br.contabil.razao.domain.repository.DemonstracoesDcaspPort;

/**
 * Caso de uso de leitura das quatro demonstrações DCASP de um exercício.
 * Conforme ADR-0047, é read model derivado do razão e exige apenas {@link Acao#LER}.
 */
public class GerarDemonstracoesDcasp {

    private static final Recurso RECURSO_DEMONSTRACOES = new Recurso("razao:demonstracoes_dcasp");

    private final ControleAcesso controleAcesso;
    private final DemonstracoesDcaspPort demonstracoes;

    public GerarDemonstracoesDcasp(ControleAcesso controleAcesso, DemonstracoesDcaspPort demonstracoes) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.demonstracoes = Objects.requireNonNull(demonstracoes, "demonstracoes");
    }

    public DemonstracoesDcasp executar(Sessao usuarioAutenticado, TenantId enteId, int exercicio) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_DEMONSTRACOES, Acao.LER);
        return demonstracoes.demonstracoes(enteId, exercicio);
    }
}
