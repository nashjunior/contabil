package br.contabil.plataforma.domain.transparencia;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Filtros públicos da transparência ativa, compartilhados por detalhe e totalização. */
public record FiltroTransparenciaPublica(
        Optional<String> estagio,
        Optional<String> credorId,
        Optional<String> orgaoId,
        Optional<LocalDate> dataInicio,
        Optional<LocalDate> dataFim,
        Optional<String> funcao,
        Optional<Long> numeroEmpenho,
        Optional<String> contratoId,
        String ordenarPor,
        DirecaoOrdenacao direcao,
        int limite,
        Optional<String> cursor) {

    public static final int LIMITE_PADRAO = 50;
    public static final int LIMITE_MAXIMO = 200;
    public static final String ORDENACAO_PADRAO = "publicadoEm";

    public FiltroTransparenciaPublica {
        Objects.requireNonNull(estagio, "estagio");
        Objects.requireNonNull(credorId, "credorId");
        Objects.requireNonNull(orgaoId, "orgaoId");
        Objects.requireNonNull(dataInicio, "dataInicio");
        Objects.requireNonNull(dataFim, "dataFim");
        Objects.requireNonNull(funcao, "funcao");
        Objects.requireNonNull(numeroEmpenho, "numeroEmpenho");
        Objects.requireNonNull(contratoId, "contratoId");
        Objects.requireNonNull(cursor, "cursor");
        ordenarPor = ordenarPor == null || ordenarPor.isBlank() ? ORDENACAO_PADRAO : ordenarPor.trim();
        direcao = direcao == null ? DirecaoOrdenacao.DESC : direcao;
        if (!CamposOrdenacaoTransparenciaPublica.ehPermitido(ordenarPor)) {
            throw new IllegalArgumentException("campo de ordenação não suportado: " + ordenarPor);
        }
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("limite deve estar entre 1 e " + LIMITE_MAXIMO);
        }
    }

    public enum DirecaoOrdenacao {
        ASC,
        DESC
    }
}
