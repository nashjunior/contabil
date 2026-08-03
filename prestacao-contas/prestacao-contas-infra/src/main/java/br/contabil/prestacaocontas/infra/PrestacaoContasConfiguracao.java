package br.contabil.prestacaocontas.infra;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.contabil.plataforma.domain.entrega.ServicoEntrega;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.prestacaocontas.application.GerarRemessaSimTceCe;
import br.contabil.prestacaocontas.application.PublicarRemessaSimTceCe;
import br.contabil.prestacaocontas.application.PublicacaoRemessaSimTceCePort;
import br.contabil.prestacaocontas.application.RemessaSimTceCePort;
import br.contabil.prestacaocontas.domain.LayoutRemessaSimTceCe;
import br.contabil.razao.application.GerarBalancete;

/** Wiring dos adapters de prestação de contas. */
@Configuration
@EnableConfigurationProperties(SimTceCeProperties.class)
public class PrestacaoContasConfiguracao {

    @Bean
    public LayoutRemessaSimTceCe layoutTabela308SimTceCe(SimTceCeProperties properties) {
        return properties.tabela308().paraDominio();
    }

    @Bean
    public RemessaSimTceCePort remessaSimTceCePort(LayoutRemessaSimTceCe layoutTabela308SimTceCe) {
        return new RemessaSimTceCeArquivoAdapter(layoutTabela308SimTceCe);
    }

    @Bean
    public GerarRemessaSimTceCe gerarRemessaSimTceCe(
            ControleAcesso controleAcesso,
            GerarBalancete gerarBalancete,
            RemessaSimTceCePort remessaSimTceCePort) {
        return new GerarRemessaSimTceCe(controleAcesso, gerarBalancete, remessaSimTceCePort);
    }

    @Bean
    public PublicacaoRemessaSimTceCePort publicacaoRemessaSimTceCePort(ServicoEntrega entrega) {
        return new PublicacaoRemessaSimTceCeOutboxAdapter(entrega);
    }

    @Bean
    public PublicarRemessaSimTceCe publicarRemessaSimTceCe(
            ControleAcesso controleAcesso,
            GerarRemessaSimTceCe gerarRemessa,
            PublicacaoRemessaSimTceCePort publicacao) {
        return new PublicarRemessaSimTceCe(controleAcesso, gerarRemessa, publicacao);
    }
}
