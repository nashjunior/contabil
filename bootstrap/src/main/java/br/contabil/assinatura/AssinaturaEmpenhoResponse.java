package br.contabil.assinatura;

import java.util.UUID;

import br.contabil.execucao.domain.DocumentoAssinadoEmpenho;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.ExecucaoInvalidaException;

record AssinaturaEmpenhoResponse(
        UUID empenhoId, String status, boolean documentoAssinado, String hashSha256, UUID idTransacao) {

    static AssinaturaEmpenhoResponse de(Empenho empenho) {
        DocumentoAssinadoEmpenho documento = empenho
                .documentoAssinado()
                .orElseThrow(() -> new ExecucaoInvalidaException(
                        "empenho_sem_documento_assinado",
                        "empenho %s sem documento assinado após AssinarEmpenho".formatted(empenho.id().valor())));
        return new AssinaturaEmpenhoResponse(
                empenho.id().valor(),
                empenho.status().name(),
                true,
                documento.hashSha256(),
                documento.idTransacao());
    }
}
