package br.contabil.plataforma.domain.transparencia;

import java.util.Set;

/** Campos documentados e aceitos na ordenação pública da transparência. */
public final class CamposOrdenacaoTransparenciaPublica {

    private static final Set<String> CAMPOS = Set.of("publicadoEm", "data", "valor", "numeroEmpenho");

    private CamposOrdenacaoTransparenciaPublica() {}

    public static boolean ehPermitido(String campo) {
        return CAMPOS.contains(campo);
    }

    public static Set<String> todos() {
        return CAMPOS;
    }
}
