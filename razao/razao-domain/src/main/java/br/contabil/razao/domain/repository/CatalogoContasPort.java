package br.contabil.razao.domain.repository;

import java.util.Optional;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.PaginaContas;

/**
 * Read port do catálogo de contas do PCASP (RAZ-117 / ADR-0030 §6) — read model
 * DERIVADO de {@code conta_pcasp} (ADR-0007), tenant-scoped por RLS
 * ({@code app.ente_id}), nunca fonte da verdade.
 *
 * <p>Duas leituras da MESMA tabela: {@link #buscar} pagina o catálogo (busca por
 * prefixo de código ou {@code descricao ilike}); {@link #existe} valida a existência
 * de uma conta no ente — usada por {@code ConsultarSaldo} para distinguir
 * "conta inexistente" (404 {@code conta_nao_encontrada}) de "conta existente sem
 * lançamento" (saldo zero legítimo), o gap 2 da ADR-0030.
 */
public interface CatalogoContasPort {

    /**
     * @param busca prefixo de {@code codigo} OU trecho de {@code descricao} ({@code ilike});
     *     vazio ⇒ sem filtro. {@code limite} já validado/clampado pelo use case.
     */
    PaginaContas buscar(TenantId enteId, Optional<String> busca, int limite, Optional<String> cursor);

    /** {@code true} se a conta existe no ente (independe de ter lançamento). */
    boolean existe(TenantId enteId, ContaContabilId contaId);
}
