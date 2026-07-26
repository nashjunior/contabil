package br.contabil.execucao.domain;

import java.util.List;
import java.util.Objects;

import br.contabil.plataforma.domain.auditoria.EventoAuditoria;

/**
 * Trilha de auditoria de uma liquidação (ADR-0029 §3) — sequência cronológica dos
 * eventos da cadeia (empenho registrado → liquidação registrada → decisão do gate),
 * lida do read model {@code AuditoriaLeitura} (append-only hash-chain, ADR-0005).
 * O ator de cada evento já vem mascarado da própria trilha (04-lgpd).
 */
public record TrilhaLiquidacao(LiquidacaoId liquidacaoId, List<EventoAuditoria> eventos) {

    public TrilhaLiquidacao {
        Objects.requireNonNull(liquidacaoId, "liquidacaoId");
        eventos = List.copyOf(Objects.requireNonNull(eventos, "eventos"));
    }
}
