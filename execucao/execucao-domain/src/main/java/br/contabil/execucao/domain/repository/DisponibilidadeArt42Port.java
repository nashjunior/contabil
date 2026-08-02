package br.contabil.execucao.domain.repository;

import java.util.Optional;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/**
 * Disponibilidade de caixa líquida da fonte de recursos informada, apurada sobre a
 * DDR (contas de controle MCASP classes 7/8, ADR-0054) — a base do gate do art. 42
 * (ADR-0044). Sem compensação entre fontes: cada consulta é escopada a uma única
 * fonte.
 *
 * <p>Bridge para {@code razao-domain.DisponibilidadePorFontePort} — implementado no
 * {@code bootstrap} (único módulo que conhece tanto {@code execucao} quanto
 * {@code razao}, mesmo padrão de {@code ExecucaoContabilPort}) para {@code execucao}
 * nunca depender de tipos do {@code razao}.
 */
public interface DisponibilidadeArt42Port {

    /**
     * Vazio quando não há como apurar a disponibilidade (ex.: contas DDR não
     * provisionadas para o ente) — mesmo tratamento do resto do roteiro DDR
     * condicional (ADR-0054): "sem controle de disponibilidade" não é "disponibilidade
     * zero", é ausência de dado.
     */
    Optional<Dinheiro> saldoDisponivel(TenantId enteId, String fonteRecurso);
}
