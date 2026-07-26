package br.contabil.plataforma.infra.entrega;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OutboxEntregaScheduler {

    private final OutboxEntregaWorker worker;
    private final OutboxEntregaProperties properties;

    OutboxEntregaScheduler(OutboxEntregaWorker worker, OutboxEntregaProperties properties) {
        this.worker = worker;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${siafic.entrega.outbox.intervalo-ms:10000}")
    void processar() {
        if (properties.isEnabled()) {
            worker.processarPendentes();
        }
    }
}
