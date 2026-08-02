package br.contabil.razao.domain;

import java.io.Serializable;

import br.contabil.plataforma.domain.Validacoes;

/**
 * Fonte/destinação de recursos (vinculação) de uma partida do razão — a
 * informação complementar {@code FR} da MSC e a chave da apuração da DDR
 * (Disponibilidade por Destinação de Recursos, contas de controle MCASP
 * classes 7/8) e do gate do art. 42 da LRF (ADR-0044, ADR-0050, ADR-0054).
 *
 * <p>Dimensão <b>simples de um único código</b> por decisão de escopo da
 * RAZ-225/[ADR-0054] — <b>não</b> é hierarquia multinível (só se aprofunda se a
 * fonte MCASP {@code [REVALIDAR]} vier a exigir). O <b>formato/comprimento exato
 * do código</b> fica {@code [REVALIDAR]} contra o MCASP edição vigente (mesma
 * marcação de docs/15 e docs/17); aqui só se garante um código não-vazio,
 * normalizado (trim) e dentro do teto de 20 do schema
 * ({@code lancamento.fonte_recurso varchar(20)}).
 */
public record FonteRecurso(String codigo) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Espelha {@code lancamento.fonte_recurso varchar(20)} — teto, não o formato final ([REVALIDAR]). */
    private static final int TAMANHO_MAXIMO = 20;

    public FonteRecurso {
        Validacoes.exigirNaoNulo(codigo, "codigo");
        codigo = codigo.strip();
        if (codigo.isEmpty()) {
            throw new IllegalArgumentException("código de fonte de recurso não pode ser vazio");
        }
        if (codigo.length() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException(
                    "código de fonte de recurso excede %d caracteres: '%s'".formatted(TAMANHO_MAXIMO, codigo));
        }
    }

    public static FonteRecurso de(String codigo) {
        return new FonteRecurso(codigo);
    }
}
