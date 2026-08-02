package br.contabil.razao.domain;

import java.io.Serializable;

import br.contabil.plataforma.domain.Validacoes;

/**
 * Informação complementar {@code NR} (natureza da receita) de uma partida do
 * razão — a classificação por natureza de receita (Portaria Interministerial
 * STN/SOF 163/2001) capturada pela origem (a {@code execucao}) na escrituração
 * (ADR-0050, ADR-0057), nunca reconstruída por heurística.
 *
 * <p>Dimensão <b>simples de um único código</b> — o <b>formato/comprimento exato</b>
 * fica {@code [REVALIDAR]} contra o leiaute MSC / MCASP edição vigente. Aqui só se
 * garante um código não-vazio, normalizado (trim) e dentro do teto de 20 do schema
 * (coluna {@code lancamento.natureza_receita varchar(20)}).
 */
public record NaturezaReceita(String codigo) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Espelha a coluna {@code varchar(20)} — teto, não o formato final ([REVALIDAR]). */
    private static final int TAMANHO_MAXIMO = 20;

    public NaturezaReceita {
        Validacoes.exigirNaoNulo(codigo, "codigo");
        codigo = codigo.strip();
        if (codigo.isEmpty()) {
            throw new IllegalArgumentException("código de natureza da receita (NR) não pode ser vazio");
        }
        if (codigo.length() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException(
                    "código de natureza da receita (NR) excede %d caracteres: '%s'"
                            .formatted(TAMANHO_MAXIMO, codigo));
        }
    }

    public static NaturezaReceita de(String codigo) {
        return new NaturezaReceita(codigo);
    }
}
