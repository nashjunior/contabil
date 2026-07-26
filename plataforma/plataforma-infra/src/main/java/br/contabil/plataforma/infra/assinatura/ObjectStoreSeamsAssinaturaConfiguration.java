package br.contabil.plataforma.infra.assinatura;

import java.net.URI;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.DocumentoParaAssinar;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.ReferenciaDocumento;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos.DocumentoTenantInvalidoException;

/**
 * Ponte entre o object store reutilizável ({@link ArmazenamentoDocumentos}, ADR-0018)
 * e os dois seams do construtor de {@link ServicoAssinaturaGovBrAvancada}: leitura do
 * documento a assinar e publicação do documento assinado.
 *
 * <p>Desligado por padrão; ativa junto com {@code contabil.objectstore.enabled} e a
 * presença do adaptador. A montagem de {@code ServicoAssinaturaGovBrAvancada} (RAZ-24)
 * injeta estes beans nos parâmetros {@code leitorDocumento}/{@code publicadorDocumentoAssinado}.
 *
 * <p><b>Isolamento multi-tenant estrutural (RAZ-45).</b> O bean {@code validadorTenant}
 * ({@link Consumer}{@code <DocumentoParaAssinar>}) é chamado por {@code ServicoAssinaturaGovBrAvancada}
 * em {@code assinar()} antes de qualquer acesso ao object store. Verifica que o prefixo
 * da URI de origem contém o UUID do {@code ente} — uma URI cross-tenant lança
 * {@link DocumentoTenantInvalidoException} e o acesso ao S3 é bloqueado (ADR-0015, ADR-0018).
 */
@Configuration
@ConditionalOnProperty(prefix = "contabil.objectstore", name = "enabled", havingValue = "true")
@ConditionalOnBean(ArmazenamentoDocumentos.class)
public class ObjectStoreSeamsAssinaturaConfiguration {

    @Bean
    Consumer<DocumentoParaAssinar> validadorTenant() {
        return doc -> validarPrefixoTenant(doc.ente(), doc.origem().uri());
    }

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

    /**
     * Verifica que {@code uri} contém o UUID do {@code ente} como primeiro segmento do
     * path ({@code /{ente.valor()}/…}). Lança {@link DocumentoTenantInvalidoException}
     * se o prefixo não bater — o acesso ao object store é bloqueado antes de ocorrer.
     */
    static void validarPrefixoTenant(TenantId ente, URI uri) {
        String prefixoEsperado = "/" + ente.valor() + "/";
        if (uri.getPath() == null || !uri.getPath().startsWith(prefixoEsperado)) {
            throw new DocumentoTenantInvalidoException(
                    "URI não pertence ao ente " + ente.valor() + ": " + uri);
        }
    }
}
