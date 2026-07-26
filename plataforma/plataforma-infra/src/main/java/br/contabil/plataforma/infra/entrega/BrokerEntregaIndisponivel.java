package br.contabil.plataforma.infra.entrega;

/**
 * Broker padrão fail-closed. Um deployment que habilita o worker deve prover um
 * {@link BrokerEntrega} real; sem isso, a mensagem fica em retentativa/DLQ.
 */
final class BrokerEntregaIndisponivel implements BrokerEntrega {

    @Override
    public void publicar(MensagemOutbox mensagem) {
        throw new IllegalStateException(
                "broker de entrega não configurado para destino %s".formatted(mensagem.destino()));
    }
}
