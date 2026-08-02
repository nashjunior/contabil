package br.contabil.razao.domain;

import java.io.Serializable;

import br.contabil.plataforma.domain.Validacoes;

/**
 * Informação complementar {@code CO} (execução orçamentária) de uma partida do
 * razão — a classificação da execução orçamentária capturada pela origem
 * (a {@code execucao}) no momento da escrituração (ADR-0050, ADR-0057), nunca
 * reconstruída por heurística sobre conta/histórico.
 *
 * <p>Dimensão <b>simples de um único código</b> na fatia MSC — o <b>formato/
 * comprimento exato</b> fica {@code [REVALIDAR]} contra o leiaute MSC / MCASP
 * edição vigente (mesma marcação de docs/15 e docs/17). Aqui só se garante um
 * código não-vazio, normalizado (trim) e dentro do teto de 20 do schema
 * (coluna {@code lancamento.execucao_orcamentaria varchar(20)}).
 */
public record ExecucaoOrcamentaria(String codigo) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Espelha a coluna {@code varchar(20)} — teto, não o formato final ([REVALIDAR]). */
    private static final int TAMANHO_MAXIMO = 20;

    public ExecucaoOrcamentaria {
        Validacoes.exigirNaoNulo(codigo, "codigo");
        codigo = codigo.strip();
        if (codigo.isEmpty()) {
            throw new IllegalArgumentException("código de execução orçamentária (CO) não pode ser vazio");
        }
        if (codigo.length() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException(
                    "código de execução orçamentária (CO) excede %d caracteres: '%s'"
                            .formatted(TAMANHO_MAXIMO, codigo));
        }
    }

    public static ExecucaoOrcamentaria de(String codigo) {
        return new ExecucaoOrcamentaria(codigo);
    }
}
