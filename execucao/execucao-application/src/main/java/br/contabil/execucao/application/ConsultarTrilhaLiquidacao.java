package br.contabil.execucao.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.TrilhaLiquidacao;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaLeitura;
import br.contabil.plataforma.domain.auditoria.AuditoriaLeitura.FiltroAuditoria;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.consulta.ConsultaPaginada;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: trilha de auditoria de uma liquidação para o modal do gate 4-eyes
 * (ADR-0029 §3). Endpoint DEDICADO servido por {@link AuditoriaLeitura} (read model,
 * ADR-0007) — não campo agregado na fila (que ficaria N×trilha). Leitura segregada da
 * escrita: {@link Acao#LER} sobre {@code execucao:liquidacao}, sem tocar {@code
 * AuditoriaEscrita}.
 *
 * <p>Monta a cadeia com duas consultas por recurso (imposto no servidor, ADR-0029 §3):
 * (1) o recurso da própria liquidação ({@code execucao:liquidacao:{id}} — registro +
 * decisão do gate); dele extrai o {@code empenhoId} do evento de registro; (2) o registro
 * do empenho da cadeia ({@code execucao:empenho:{empenhoId}}). Os eventos vêm com ator já
 * mascarado da trilha; aqui só são unidos e ordenados por {@code momento}.
 */
public class ConsultarTrilhaLiquidacao {

    private static final Recurso RECURSO_LIQUIDACAO = new Recurso("execucao:liquidacao");

    static final String TIPO_EMPENHO_REGISTRADO = "execucao_empenho_registrado";
    static final String TIPO_LIQUIDACAO_REGISTRADA = "execucao_liquidacao_registrada";
    static final String TIPO_APROVACAO_DECIDIDA = "execucao_pagamento_aprovacao_decidida";

    /** Só os eventos que compõem a trilha do gate — descarta ruído de outro writer no mesmo recurso. */
    private static final Set<String> TIPOS_TRILHA_LIQUIDACAO =
            Set.of(TIPO_LIQUIDACAO_REGISTRADA, TIPO_APROVACAO_DECIDIDA);

    /**
     * Janela ampla: a trilha é a linha do tempo inteira do agregado, não um recorte
     * temporal. Uma liquidação acumula um punhado de eventos (registro + decisão — a dupla
     * decisão é barrada com 409), muito abaixo do teto.
     */
    private static final Instant INICIO_TRILHA = Instant.EPOCH;
    private static final Instant FIM_TRILHA = Instant.parse("2999-12-31T23:59:59Z");
    private static final int MAX_EVENTOS = ConsultaPaginada.POR_PAGINA_MAXIMO;

    private final ControleAcesso controleAcesso;
    private final AuditoriaLeitura auditoriaLeitura;

    public ConsultarTrilhaLiquidacao(ControleAcesso controleAcesso, AuditoriaLeitura auditoriaLeitura) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.auditoriaLeitura = Objects.requireNonNull(auditoriaLeitura, "auditoriaLeitura");
    }

    public TrilhaLiquidacao executar(Sessao usuarioAutenticado, TenantId enteId, LiquidacaoId liquidacaoId) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_LIQUIDACAO, Acao.LER);

        List<EventoAuditoria> eventosLiquidacao =
                consultarPorRecurso(enteId, "execucao:liquidacao:%s".formatted(liquidacaoId.valor()), Optional.empty())
                        .stream()
                        .filter(evento -> TIPOS_TRILHA_LIQUIDACAO.contains(evento.tipo()))
                        .toList();

        List<EventoAuditoria> eventos = new ArrayList<>(eventosLiquidacao);
        empenhoIdDaCadeia(eventosLiquidacao)
                .ifPresent(empenhoId -> eventos.addAll(consultarPorRecurso(
                        enteId, "execucao:empenho:%s".formatted(empenhoId), Optional.of(TIPO_EMPENHO_REGISTRADO))));

        eventos.sort(Comparator.comparing(EventoAuditoria::momento));
        return new TrilhaLiquidacao(liquidacaoId, eventos);
    }

    private List<EventoAuditoria> consultarPorRecurso(TenantId enteId, String recurso, Optional<String> tipo) {
        FiltroAuditoria filtro =
                new FiltroAuditoria(tipo, Optional.empty(), Optional.of(recurso), INICIO_TRILHA, FIM_TRILHA);
        return auditoriaLeitura
                .consultar(enteId, new ConsultaPaginada<>(filtro, 1, MAX_EVENTOS, List.of()))
                .itens();
    }

    private static Optional<String> empenhoIdDaCadeia(List<EventoAuditoria> eventosLiquidacao) {
        return eventosLiquidacao.stream()
                .filter(evento -> TIPO_LIQUIDACAO_REGISTRADA.equals(evento.tipo()))
                .map(evento -> evento.detalhes().get("empenhoId"))
                .filter(Objects::nonNull)
                .findFirst();
    }
}
