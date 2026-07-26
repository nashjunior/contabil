package br.contabil.execucao.infra.documento;

import org.springframework.scheduling.annotation.Scheduled;

/** Registrado como {@code @Bean} por {@link EmpenhoDocumentoPendenteConfiguration}, não {@code @Component}. */
class EmpenhoDocumentoPendenteScheduler {

    private final EmpenhoDocumentoPendenteWorker worker;
    private final EmpenhoDocumentoPendenteProperties properties;

    EmpenhoDocumentoPendenteScheduler(EmpenhoDocumentoPendenteWorker worker, EmpenhoDocumentoPendenteProperties properties) {
        this.worker = worker;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${siafic.execucao.empenho-documento.outbox.intervalo-ms:10000}")
    void processar() {
        if (properties.isEnabled()) {
            worker.processarPendentes();
        }
    }
}
