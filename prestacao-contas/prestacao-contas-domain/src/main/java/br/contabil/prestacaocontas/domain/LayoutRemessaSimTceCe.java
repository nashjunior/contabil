package br.contabil.prestacaocontas.domain;

import java.util.List;
import java.util.Objects;

/** Leiaute configurável de uma tabela PGI/SIM. */
public record LayoutRemessaSimTceCe(
        String tabela,
        String prefixoArquivo,
        String extensaoArquivo,
        String delimitador,
        int quantidadeCampos,
        List<CampoLayoutRemessaSimTceCe> campos) {

    public LayoutRemessaSimTceCe {
        tabela = obrigatorio(tabela, "tabela");
        prefixoArquivo = obrigatorio(prefixoArquivo, "prefixoArquivo");
        extensaoArquivo = obrigatorio(extensaoArquivo, "extensaoArquivo").toUpperCase();
        delimitador = obrigatorio(delimitador, "delimitador");
        Objects.requireNonNull(campos, "campos");
        campos = List.copyOf(campos);
        if (quantidadeCampos < 1) {
            throw new IllegalArgumentException("quantidadeCampos deve ser positiva");
        }
        if (campos.size() != quantidadeCampos) {
            throw new IllegalArgumentException(
                    "layout SIM %s espera %d campos, mas recebeu %d"
                            .formatted(tabela, quantidadeCampos, campos.size()));
        }
        if (delimitador.length() != 1) {
            throw new IllegalArgumentException("delimitador do SIM deve ter um caractere");
        }
    }

    public String nomeArquivo(int exercicio, int mes) {
        validarCompetencia(exercicio, mes);
        return "%s%d%02d.%s".formatted(prefixoArquivo, exercicio, mes, extensaoArquivo);
    }

    public String nomeZip(int exercicio, int mes) {
        validarCompetencia(exercicio, mes);
        return "%s%d%02d.zip".formatted(prefixoArquivo, exercicio, mes);
    }

    private static void validarCompetencia(int exercicio, int mes) {
        if (exercicio < 1) {
            throw new IllegalArgumentException("exercicio deve ser positivo");
        }
        if (mes < 1 || mes > 12) {
            throw new IllegalArgumentException("mes do SIM deve estar entre 1 e 12");
        }
    }

    private static String obrigatorio(String valor, String campo) {
        Objects.requireNonNull(valor, campo);
        String normalizado = valor.strip();
        if (normalizado.isEmpty()) {
            throw new IllegalArgumentException(campo + " não pode ser vazio");
        }
        return normalizado;
    }
}
