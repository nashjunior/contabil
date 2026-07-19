package br.contabil.plataforma.infra.assinatura;

import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.ReferenciaDocumento;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
import java.net.URI;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ponte entre o object store reutilizável ({@link ArmazenamentoDocumentos}, ADR-0018)
 * e os dois seams do construtor de {@link ServicoAssinaturaGovBrAvancada}: leitura do
 * documento a assinar e publicação do documento assinado.
 *
 * <p>Desligado por padrão; ativa junto com {@code contabil.objectstore.enabled} e a
 * presença do adaptador. A montagem de {@code ServicoAssinaturaGovBrAvancada} (RAZ-24)
 * injeta estes beans nos parâmetros {@code leitorDocumento}/{@code publicadorDocumentoAssinado}.
 */
@Configuration
@ConditionalOnProperty(prefix = "contabil.objectstore", name = "enabled", havingValue = "true")
@ConditionalOnBean(ArmazenamentoDocumentos.class)
public class ObjectStoreSeamsAssinaturaConfiguration {

    @Bean
    Function<ReferenciaDocumento, byte[]> leitorDocumento(ArmazenamentoDocumentos armazenamento) {
        return referencia -> armazenamento.ler(referencia.uri());
    }

    @Bean
    BiFunction<byte[], ReferenciaDocumento, ReferenciaDocumento> publicadorDocumentoAssinado(
            ArmazenamentoDocumentos armazenamento) {
        return (conteudo, origem) ->
                new ReferenciaDocumento(armazenamento.armazenar(conteudo, uriAssinada(origem.uri())));
    }

    /**
     * URI do documento assinado, derivada da origem de forma append-only:
     * {@code …/x.pdf} → {@code …/x-assinado.pdf} (mesmo bucket e path). Nunca
     * sobrescreve a origem; correção segue estorno + novo documento (docs/10).
     */
    static URI uriAssinada(URI origem) {
        String path = origem.getPath();
        int ponto = path.lastIndexOf('.');
        int barra = path.lastIndexOf('/');
        String novoPath = (ponto > barra)
                ? path.substring(0, ponto) + "-assinado" + path.substring(ponto)
                : path + "-assinado";
        return URI.create(origem.getScheme() + "://" + origem.getAuthority() + novoPath);
    }
}
