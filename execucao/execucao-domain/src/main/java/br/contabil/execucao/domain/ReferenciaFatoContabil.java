package br.contabil.execucao.domain;

import java.io.Serializable;
import java.util.UUID;

import br.contabil.plataforma.domain.Validacoes;

/**
 * Referência da execução ao fato contábil que a escrituração produziu no razão
 * (ADR-0028) — VO do módulo consumidor, nunca {@code razao.domain.FatoContabilId}
 * (fora do classpath de {@code execucao-domain}). O {@code UUID} de fio é
 * contido no {@code ExecucaoContabilPortAdapter} (bootstrap), única classe que
 * conhece os dois módulos.
 */
public record ReferenciaFatoContabil(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ReferenciaFatoContabil {
        Validacoes.exigirNaoNulo(valor, "ReferenciaFatoContabil");
    }

    public static ReferenciaFatoContabil de(String uuid) {
        return new ReferenciaFatoContabil(UUID.fromString(uuid));
    }
}
