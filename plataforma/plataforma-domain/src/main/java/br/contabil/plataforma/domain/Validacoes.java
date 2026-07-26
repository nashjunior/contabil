package br.contabil.plataforma.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Centraliza o {@code Objects.requireNonNull(x, "x não pode ser nulo")} repetido
 * em cada compact constructor do domínio (S1192 em interfaces com múltiplos
 * records — ver ExecucaoContabilPort). Respeita a concordância de gênero em
 * PT-BR: campos femininos exigem "nula", os demais (default) exigem "nulo".
 */
public final class Validacoes {

    // Lista fechada por vocabulário, não por regra gramatical — um campo feminino novo
    // que não entrar aqui vira "nulo" (masculino) por padrão, sem aviso do compilador.
    private static final Set<String> CAMPOS_FEMININOS = Set.of(
            "dataFato",
            "dataCompetencia",
            "dataEmissao",
            "dataHoraRegistro",
            "natureza",
            "ordemBancaria",
            "classificacaoOrcamentaria",
            "referenciaExterna",
            "origem",
            "ReferenciaFatoContabil");

    private Validacoes() {}

    public static <T> T exigirNaoNulo(T valor, String campo) {
        String sufixo = CAMPOS_FEMININOS.contains(campo) ? " não pode ser nula" : " não pode ser nulo";
        return Objects.requireNonNull(valor, campo + sufixo);
    }
}
