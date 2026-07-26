package br.contabil.plataforma.infra.entrega;

/**
 * Porta de infraestrutura para o broker/fila da entrega garantida.
 *
 * <p>Fica na infra porque representa o mecanismo externo de despacho. O contrato
 * de negócio segue sendo {@code ServicoEntrega}: gravar a intenção no outbox.
 */
public interface BrokerEntrega {

    void publicar(MensagemOutbox mensagem);
}
