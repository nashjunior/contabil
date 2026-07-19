package br.contabil.plataforma.domain.documento;

import java.net.URI;

/**
 * Porta de armazenamento de documentos binários em object store/GED (ADR-0009 —
 * o QUÊ; ADR-0018 — o COMO). Capacidade de plataforma reutilizável: assinatura
 * hoje (lê o documento a assinar, grava o assinado), anexos amanhã.
 *
 * <p>Contrato estável: o conteúdo vive fora da base contábil (nunca BLOB); o fato
 * guarda apenas a referência (a {@link URI} do objeto). Toda gravação é cifrada em
 * repouso pelo adaptador (ADR-0018).
 *
 * <p><b>Isolamento multi-tenant.</b> A porta é dirigida por {@link URI} porque o
 * seam de assinatura ({@code ServicoAssinatura.ReferenciaDocumento(URI)}) assim o
 * é. O escopo por tenant é responsabilidade do produtor da URI: a chave do objeto
 * deve ser namespaced pelo {@code ente} (ex.: {@code s3://bucket/{ente}/...}),
 * reforçado por política de bucket/IAM no ambiente. Documentado no ADR-0018.
 *
 * <p><b>Append-only de fato.</b> Uma nova versão (ex.: documento assinado) é um
 * novo objeto com nova URI; correção segue estorno + novo documento (docs/10),
 * nunca sobrescrita da chave existente.
 */
public interface ArmazenamentoDocumentos {

    /** Lê o conteúdo do objeto referenciado. Objeto ausente é erro (lança). */
    byte[] ler(URI referencia);

    /**
     * Grava {@code conteudo} no {@code destino} (cifrado em repouso) e devolve a
     * URI efetiva do objeto persistido.
     */
    URI armazenar(byte[] conteudo, URI destino);
}
