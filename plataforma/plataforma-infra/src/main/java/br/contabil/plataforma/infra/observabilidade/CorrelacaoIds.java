package br.contabil.plataforma.infra.observabilidade;

import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

public final class CorrelacaoIds {

    public static final String CABECALHO = "X-Correlation-Id";
    public static final String MDC_CORRELATION_ID = "correlationId";

    private static final int TAMANHO_MAXIMO = 128;
    private static final Pattern VALOR_SEGURO = Pattern.compile("[A-Za-z0-9._:-]{1," + TAMANHO_MAXIMO + "}");

    private CorrelacaoIds() {}

    public static String atualOuNovo() {
        String atual = MDC.get(MDC_CORRELATION_ID);
        return atual != null && !atual.isBlank() ? atual : novo();
    }

    public static String resolver(String recebido) {
        if (recebido == null) {
            return novo();
        }
        String normalizado = recebido.trim();
        return VALOR_SEGURO.matcher(normalizado).matches() ? normalizado : novo();
    }

    public static Escopo escopo(String correlationId) {
        String anterior = MDC.get(MDC_CORRELATION_ID);
        MDC.put(MDC_CORRELATION_ID, resolver(correlationId));
        return () -> restaurar(anterior);
    }

    public static String novo() {
        return UUID.randomUUID().toString();
    }

    private static void restaurar(String anterior) {
        if (anterior == null) {
            MDC.remove(MDC_CORRELATION_ID);
            return;
        }
        MDC.put(MDC_CORRELATION_ID, anterior);
    }

    public interface Escopo extends AutoCloseable {
        @Override
        void close();
    }
}
