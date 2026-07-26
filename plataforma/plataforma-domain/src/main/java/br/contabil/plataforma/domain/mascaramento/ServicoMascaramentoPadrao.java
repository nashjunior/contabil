package br.contabil.plataforma.domain.mascaramento;

import java.util.Objects;

/**
 * Política padrão de mascaramento LGPD do SIAFIC.
 *
 * <p>Implementa a regra pública de {@code docs/transversais/04-lgpd.md}: CPF expõe no
 * máximo os 3 dígitos centrais ({@code ***.456.***-**}); RG, endereço, contato, dados
 * bancários, dados sensíveis e campos não classificados são suprimidos na transparência.
 * Audiências internas recebem o valor integral mediante contexto com base legal.
 */
public final class ServicoMascaramentoPadrao implements ServicoMascaramento {

    private static final String SUPRIMIDO = "[SUPRIMIDO]";
    private static final String CPF_INVALIDO_MASCARADO = "***";

    @Override
    public String mascarar(CampoSensivel campo, ContextoAcesso contexto, Audiencia audiencia) {
        Objects.requireNonNull(campo, "campo sensível");
        Objects.requireNonNull(contexto, "contexto de acesso");
        Objects.requireNonNull(audiencia, "audiência");
        exigirBaseLegal(contexto);

        if (audiencia != Audiencia.PORTAL_PUBLICO) {
            return campo.valor();
        }

        return switch (campo.categoria()) {
            case CPF -> mascararCpf(campo.valor());
            case NOME -> campo.valor();
            case ENDERECO, CONTATO, DADO_BANCARIO, DADO_SENSIVEL, OUTRO -> SUPRIMIDO;
        };
    }

    private static void exigirBaseLegal(ContextoAcesso contexto) {
        if (contexto.finalidade().isBlank()) {
            throw new SemBaseLegalException("finalidade de tratamento não informada");
        }
    }

    static String mascararCpf(String valor) {
        String digitos = valor.replaceAll("\\D", "");
        if (digitos.length() != 11) {
            return CPF_INVALIDO_MASCARADO;
        }
        return "***.%s.***-**".formatted(digitos.substring(3, 6));
    }
}
