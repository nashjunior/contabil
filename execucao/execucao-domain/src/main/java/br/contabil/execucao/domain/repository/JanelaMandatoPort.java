package br.contabil.execucao.domain.repository;

import java.util.Optional;

import br.contabil.execucao.domain.JanelaMandato;
import br.contabil.plataforma.domain.TenantId;

/**
 * Config do ente (ADR-0044): datas de início/fim do mandato vigente, parametrizáveis,
 * nunca hard-coded — base da janela de bloqueio do art. 42 da LRF.
 */
public interface JanelaMandatoPort {

    /** Vazio quando o ente ainda não configurou o mandato vigente. */
    Optional<JanelaMandato> buscar(TenantId enteId);
}
