package br.contabil.plataforma.domain.consulta;

import java.util.List;
import java.util.Objects;

/**
 * Consulta paginada parametrizada por um filtro de domínio.
 *
 * <p>O tenant continua fora deste objeto: contratos cross-módulo recebem
 * {@code TenantId} no método do port/use case e a RLS valida o escopo na borda.
 */
public record ConsultaPaginada<F>(F filtro, int pagina, int porPagina, List<Ordenacao> ordenacoes) {

    public static final int PRIMEIRA_PAGINA = 1;
    public static final int POR_PAGINA_PADRAO = 20;
    public static final int POR_PAGINA_MAXIMO = 100;

    public ConsultaPaginada {
        Objects.requireNonNull(filtro, "filtro");
        if (pagina < PRIMEIRA_PAGINA) {
            throw new IllegalArgumentException("página deve ser maior ou igual a 1");
        }
        if (porPagina < 1 || porPagina > POR_PAGINA_MAXIMO) {
            throw new IllegalArgumentException("itens por página deve estar entre 1 e " + POR_PAGINA_MAXIMO);
        }
        ordenacoes = List.copyOf(Objects.requireNonNull(ordenacoes, "ordenações"));
    }

    public static <F> ConsultaPaginada<F> primeiraPagina(F filtro) {
        return new ConsultaPaginada<>(filtro, PRIMEIRA_PAGINA, POR_PAGINA_PADRAO, List.of());
    }
}
