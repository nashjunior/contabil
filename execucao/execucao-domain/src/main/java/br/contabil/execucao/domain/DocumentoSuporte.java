package br.contabil.execucao.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import br.contabil.plataforma.domain.Validacoes;

/**
 * Documento que fundamenta a liquidação (Lei 4.320/1964 art. 63, §2º).
 *
 * <p>A referência externa fica opcional para permitir nota fiscal, contrato,
 * medição ou comprovante legado sem forçar um formato único na F1.
 */
public record DocumentoSuporte(String tipo, String numero, LocalDate dataEmissao, Optional<String> referenciaExterna) {

    public DocumentoSuporte {
        Objects.requireNonNull(tipo, "tipo do documento não pode ser nulo");
        Objects.requireNonNull(numero, "número do documento não pode ser nulo");
        Validacoes.exigirNaoNulo(dataEmissao, "dataEmissao");
        Validacoes.exigirNaoNulo(referenciaExterna, "referenciaExterna");
        if (tipo.isBlank()) {
            throw new IllegalArgumentException("tipo do documento não pode ser vazio");
        }
        if (numero.isBlank()) {
            throw new IllegalArgumentException("número do documento não pode ser vazio");
        }
        referenciaExterna.ifPresent(referencia -> {
            if (referencia.isBlank()) {
                throw new IllegalArgumentException("referência externa não pode ser vazia");
            }
        });
    }

    public static DocumentoSuporte de(String tipo, String numero, LocalDate dataEmissao) {
        return new DocumentoSuporte(tipo, numero, dataEmissao, Optional.empty());
    }
}
