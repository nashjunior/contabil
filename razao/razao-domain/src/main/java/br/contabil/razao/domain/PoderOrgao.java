package br.contabil.razao.domain;

import java.io.Serializable;

import br.contabil.plataforma.domain.Validacoes;

/**
 * Informação complementar {@code PO} (Poder/Órgão) de um fato contábil — a
 * dimensão organizacional da unidade emitente, capturada pela origem
 * (a {@code execucao}) na escrituração (ADR-0050, ADR-0057).
 *
 * <p>Diferente das IC de partida (FR/CO/NR/ND/FS/AI), a {@code PO} é <b>fato-wide</b>:
 * todas as partidas de um mesmo {@link FatoContabil} compartilham a unidade
 * emitente (ADR-0050 — "dimensões genuinamente fato-wide, ex. PO da unidade
 * emitente, podem ficar no fato"). Também sustenta a granularidade UO/UPC/UG do
 * SIM/TCE-CE (docs/16, ADR-0051).
 *
 * <p>Dimensão <b>simples de um único código</b>; o <b>formato/comprimento exato</b>
 * fica {@code [REVALIDAR]} contra o leiaute MSC / MCASP edição vigente. Aqui só se
 * garante um código não-vazio, normalizado (trim) e dentro do teto de 20 do schema
 * (coluna {@code fato_contabil.poder_orgao varchar(20)}).
 */
public record PoderOrgao(String codigo) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Espelha a coluna {@code varchar(20)} — teto, não o formato final ([REVALIDAR]). */
    private static final int TAMANHO_MAXIMO = 20;

    public PoderOrgao {
        Validacoes.exigirNaoNulo(codigo, "codigo");
        codigo = codigo.strip();
        if (codigo.isEmpty()) {
            throw new IllegalArgumentException("código de Poder/Órgão (PO) não pode ser vazio");
        }
        if (codigo.length() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException(
                    "código de Poder/Órgão (PO) excede %d caracteres: '%s'".formatted(TAMANHO_MAXIMO, codigo));
        }
    }

    public static PoderOrgao de(String codigo) {
        return new PoderOrgao(codigo);
    }
}
