package br.contabil.plataforma.infra.entrega;

/** Falha não reprocessável: o worker envia a mensagem para DLQ. */
public class FalhaPermanenteEntregaException extends RuntimeException {

    public FalhaPermanenteEntregaException(String message) {
        super(message);
    }

    public FalhaPermanenteEntregaException(String message, Throwable cause) {
        super(message, cause);
    }
}
