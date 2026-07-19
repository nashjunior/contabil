package br.contabil.plataforma.domain;

/**
 * Contrato dos erros dos serviços de plataforma (doc 11 §Contratos/Interfaces).
 *
 * <p>Toda exceção de um port transversal expõe um {@link #codigo()} <b>estável e
 * legível por máquina</b> (ex.: {@code "nao_autenticado"}, {@code "sem_escopo"}).
 * O código faz parte do contrato: mudá-lo é <i>breaking change</i> para os módulos
 * consumidores e para a trilha de auditoria, e deve ser versionado em ADR.
 *
 * <p>Seed dos contratos transversais (RAZ-8). Pertence ao shared kernel: camada
 * PURA, sem Spring/JPA.
 */
public interface ErroContrato {

    /** Código estável do erro, conforme a tabela de contratos do doc 11 §Contratos. */
    String codigo();
}
