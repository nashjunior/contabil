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
 * <p><b>Isolamento multi-tenant estrutural (RAZ-45).</b> A porta é dirigida por
 * {@link URI} porque o seam de assinatura
 * ({@code ServicoAssinatura.ReferenciaDocumento(URI)}) assim o é. A chave do
 * objeto é namespaced pelo {@code ente} (ex.: {@code s3://bucket/{ente}/...}),
 * reforçado por política de bucket/IAM. A validação em código é responsabilidade
 * da ponte ({@code ObjectStoreSeamsAssinaturaConfiguration}), que recebe
 * {@code DocumentoParaAssinar} com o {@code ente} ({@link br.contabil.plataforma.domain.TenantId})
 * e confere que o prefixo da URI contém o UUID do tenant antes de delegar a
 * esta porta — nenhum acesso ao object store ocorre sem essa checagem (RAZ-45).
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

    /**
     * Erro {@code documento_tenant_invalido}: URI não pertence ao {@code ente} informado —
     * violação de isolamento multi-tenant (ADR-0015, ADR-0018, RAZ-45).
     */
    final class DocumentoTenantInvalidoException extends RuntimeException implements ErroContrato {
        public DocumentoTenantInvalidoException(String mensagem) {
            super(mensagem);
        }

        @Override
        public String codigo() {
            return "documento_tenant_invalido";
        }
    }
}
