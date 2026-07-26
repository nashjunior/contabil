package br.contabil.execucao.domain;

import java.util.Objects;

/**
 * Beneficiário nominal do pagamento. CPF/CNPJ é dado pessoal e deve ser tratado
 * na fronteira por mascaramento/RBAC; o domínio preserva o valor íntegro.
 */
public record Beneficiario(String nome, String cpfCnpj) {

    public Beneficiario {
        Objects.requireNonNull(nome, "nome do beneficiário não pode ser nulo");
        Objects.requireNonNull(cpfCnpj, "CPF/CNPJ do beneficiário não pode ser nulo");
        if (nome.isBlank()) {
            throw new IllegalArgumentException("nome do beneficiário não pode ser vazio");
        }
        if (cpfCnpj.isBlank()) {
            throw new IllegalArgumentException("CPF/CNPJ do beneficiário não pode ser vazio");
        }
    }
}
