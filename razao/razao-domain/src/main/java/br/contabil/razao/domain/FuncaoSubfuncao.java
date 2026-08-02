package br.contabil.razao.domain;

import java.io.Serializable;

import br.contabil.plataforma.domain.Validacoes;

/**
 * Informação complementar {@code FS} (função/subfunção) de uma partida do razão —
 * a classificação funcional da despesa (Portaria MPOG 42/1999) capturada pela
 * origem (a {@code execucao}) na escrituração (ADR-0050, ADR-0057), nunca
 * reconstruída por heurística.
 *
 * <p>Dimensão <b>simples de um único código</b> (função+subfunção concatenadas) —
 * o <b>formato/comprimento exato</b> fica {@code [REVALIDAR]} contra o leiaute
 * MSC / MCASP edição vigente. Aqui só se garante um código não-vazio, normalizado
 * (trim) e dentro do teto de 20 do schema (coluna {@code lancamento.funcao_subfuncao varchar(20)}).
 */
public record FuncaoSubfuncao(String codigo) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Espelha a coluna {@code varchar(20)} — teto, não o formato final ([REVALIDAR]). */
    private static final int TAMANHO_MAXIMO = 20;

    public FuncaoSubfuncao {
        Validacoes.exigirNaoNulo(codigo, "codigo");
        codigo = codigo.strip();
        if (codigo.isEmpty()) {
            throw new IllegalArgumentException("código de função/subfunção (FS) não pode ser vazio");
        }
        if (codigo.length() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException(
                    "código de função/subfunção (FS) excede %d caracteres: '%s'"
                            .formatted(TAMANHO_MAXIMO, codigo));
        }
    }

    public static FuncaoSubfuncao de(String codigo) {
        return new FuncaoSubfuncao(codigo);
    }
}
