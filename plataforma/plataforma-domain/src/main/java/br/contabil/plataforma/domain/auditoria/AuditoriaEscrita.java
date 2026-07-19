package br.contabil.plataforma.domain.auditoria;

import br.contabil.plataforma.domain.ErroContrato;
import java.util.Objects;
import java.util.UUID;

/**
 * Contrato de <b>escrita</b> da trilha de auditoria (doc 11 §Contratos; ADR-0005
 * append-only com hash-chain em store segregado).
 *
 * <p>{@code append} é <b>imutável</b> e <b>nunca falha silenciosamente</b>: se não
 * puder persistir com integridade, lança {@link FalhaAppendException} — a operação
 * de negócio que dependia da trilha deve abortar, jamais seguir sem registro.
 * Classe separada da {@link AuditoriaLeitura} (segregação escrita/leitura, ADR-0007).
 */
public interface AuditoriaEscrita {

    /**
     * Anexa um evento à trilha e devolve sua posição encadeada (hash-chain).
     *
     * @throws FalhaAppendException não foi possível registrar com integridade
     */
    EntradaTrilha append(EventoAuditoria evento);

    /** Posição do evento na cadeia: hash próprio + hash anterior + sequência (ADR-0005). */
    record EntradaTrilha(UUID id, String hashEvento, String hashAnterior, long sequencia) {
        public EntradaTrilha {
            Objects.requireNonNull(id, "id da entrada");
            Objects.requireNonNull(hashEvento, "hash do evento");
            // hashAnterior pode ser nulo apenas no evento gênesis da cadeia.
        }
    }

    /** Erro {@code append_falhou}: a trilha não pôde registrar o evento com integridade. */
    final class FalhaAppendException extends RuntimeException implements ErroContrato {
        public FalhaAppendException(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }

        @Override
        public String codigo() {
            return "append_falhou";
        }
    }
}
