package br.contabil.prestacaocontas.application;

import java.util.List;
import java.util.Objects;

import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.IdEntrega;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.prestacaocontas.domain.DimensaoOrganizacionalSimTceCe;
import br.contabil.prestacaocontas.domain.RemessaSimTceCe;

/** Enfileira a remessa SIM no outbox idempotente; o envio externo fica assíncrono. */
public class PublicarRemessaSimTceCe {

    private static final Recurso RECURSO_REMESSA_SIM = new Recurso("prestacao-contas:sim-tce-ce:remessa");

    private final ControleAcesso controleAcesso;
    private final GerarRemessaSimTceCe gerarRemessa;
    private final PublicacaoRemessaSimTceCePort publicacao;

    public PublicarRemessaSimTceCe(
            ControleAcesso controleAcesso,
            GerarRemessaSimTceCe gerarRemessa,
            PublicacaoRemessaSimTceCePort publicacao) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.gerarRemessa = Objects.requireNonNull(gerarRemessa, "gerarRemessa");
        this.publicacao = Objects.requireNonNull(publicacao, "publicacao");
    }

    public IdEntrega executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            int exercicio,
            int mes,
            List<DimensaoOrganizacionalSimTceCe> dimensoes,
            ChaveIdempotencia chave) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_REMESSA_SIM, Acao.PUBLICAR);
        RemessaSimTceCe remessa = gerarRemessa.executar(usuarioAutenticado, enteId, exercicio, mes, dimensoes);
        return publicacao.publicar(remessa, chave);
    }
}
