package br.contabil.plataforma.domain.documento;

import br.contabil.plataforma.domain.ErroContrato;
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
 * <p><b>Isolamento multi-tenant — ainda 100% convenção (RAZ-45, pendente).</b> A
 * porta é dirigida por {@link URI} porque o seam de assinatura
 * ({@code ServicoAssinatura.ReferenciaDocumento(URI)}) assim o é. O escopo por
 * tenant é responsabilidade do produtor da URI: a chave do objeto deve ser
 * namespaced pelo {@code ente} (ex.: {@code s3://bucket/{ente}/...}), reforçado
 * por política de bucket/IAM no ambiente. Nem a porta nem o adaptador verificam
 * em código que a URI lida/gravada pertence ao {@code ente} de quem chama — a
 * correção desenhada (validar o prefixo antes de delegar a esta porta, carregando
 * {@code TenantId} pelo seam de assinatura) é pendência explícita do ADR-0018,
 * ainda não implementada.
 *
 * <p><b>Append-only de fato.</b> Uma nova versão (ex.: documento assinado) é um
 * novo objeto com nova URI; correção segue estorno + novo documento (docs/10),
 * nunca sobrescrita da chave existente. O adaptador aplica escrita condicional
 * (nunca sobrescreve uma chave já ocupada) — não depende só da forma como a URI é
 * derivada (RAZ-45).
 */
public interface ArmazenamentoDocumentos {

    /**
     * Lê o conteúdo do objeto referenciado.
     *
     * @throws DocumentoNaoEncontradoException objeto ausente na referência informada
     */
    byte[] ler(URI referencia);

    /**
     * Grava {@code conteudo} no {@code destino} (cifrado em repouso, escrita
     * condicional) e devolve a URI efetiva do objeto persistido.
     *
     * @throws DocumentoJaExistenteException já existe objeto na chave de destino
     *         (append-only — correção é estorno + novo documento, docs/10)
     */
    URI armazenar(byte[] conteudo, URI destino);

    // ---- Erros do contrato (doc 11 §Contratos, ADR-0014) ----------------------

    /** Erro {@code documento_nao_encontrado}: objeto ausente na referência informada. */
    final class DocumentoNaoEncontradoException extends RuntimeException implements ErroContrato {
        public DocumentoNaoEncontradoException(String mensagem) {
            super(mensagem);
        }

        @Override
        public String codigo() {
            return "documento_nao_encontrado";
        }
    }

    /**
     * Erro {@code documento_ja_existente}: escrita rejeitada porque já existe
     * objeto na chave de destino — guarda append-only (docs/10).
     */
    final class DocumentoJaExistenteException extends RuntimeException implements ErroContrato {
        public DocumentoJaExistenteException(String mensagem) {
            super(mensagem);
        }

        @Override
        public String codigo() {
            return "documento_ja_existente";
        }
    }
}
