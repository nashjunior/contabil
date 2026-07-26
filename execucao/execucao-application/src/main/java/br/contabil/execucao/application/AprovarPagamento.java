package br.contabil.execucao.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import br.contabil.execucao.domain.DecisaoAprovacao;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.execucao.domain.repository.LiquidacaoRepository;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: materializa o gate {@code APROVAR} do pagamento (ADR-0023, ratificado em RAZ-88).
 *
 * <p>Fecha a lacuna de segregação de {@link RegistrarPagamento}, que sozinho só checava {@code
 * Acao.CRIAR}: aqui exigimos {@code Acao.APROVAR} sobre {@code execucao:pagamento} (RBAC+MFA,
 * ADR-0016) e recusamos auto-aprovação contra a identidade de quem registrou o empenho e a
 * liquidação da mesma cadeia (Regra 9) — verificação que vive dentro do agregado {@link
 * Liquidacao} (ver {@link Liquidacao#aprovar} / {@link Liquidacao#devolver}). Contrato: {@code
 * POST .../liquidacoes/{id}/aprovacao} (fluxo §6.6).
 */
public class AprovarPagamento {

    private static final Recurso RECURSO_PAGAMENTO = new Recurso("execucao:pagamento");

    private final ControleAcesso controleAcesso;
    private final LiquidacaoRepository liquidacaoRepositorio;
    private final EmpenhoRepository empenhoRepositorio;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public AprovarPagamento(
            ControleAcesso controleAcesso,
            LiquidacaoRepository liquidacaoRepositorio,
            EmpenhoRepository empenhoRepositorio,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.controleAcesso = controleAcesso;
        this.liquidacaoRepositorio = liquidacaoRepositorio;
        this.empenhoRepositorio = empenhoRepositorio;
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.clock = clock;
    }

    public Liquidacao executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            LiquidacaoId liquidacaoId,
            DecisaoAprovacao decisao,
            Optional<String> motivo) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_PAGAMENTO, Acao.APROVAR);

        Liquidacao liquidacao = liquidacaoRepositorio
                .buscarPorId(enteId, liquidacaoId)
                .orElseThrow(() -> new ExecucaoInvalidaException(
                        "liquidacao_nao_encontrada", "liquidação %s não encontrada para o ente".formatted(liquidacaoId)));
        Empenho empenho = empenhoRepositorio
                .buscarPorId(enteId, liquidacao.empenhoId())
                .orElseThrow(() -> new ExecucaoInvalidaException(
                        "empenho_nao_encontrado",
                        "empenho %s não encontrado para o ente".formatted(liquidacao.empenhoId())));

        Cpf aprovador = usuarioAutenticado.titular();
        Liquidacao decidida =
                switch (decisao) {
                    case APROVAR -> liquidacao.aprovar(aprovador, empenho.autor());
                    case DEVOLVER -> liquidacao.devolver(aprovador, empenho.autor(), motivo.orElse(null));
                };

        liquidacaoRepositorio.atualizarDecisaoAprovacao(decidida);

        auditoria.append(new EventoAuditoria(
                enteId,
                "execucao_pagamento_aprovacao_decidida",
                aprovador.mascarado(),
                "execucao:liquidacao:%s".formatted(liquidacaoId.valor()),
                Instant.now(clock),
                Map.of(
                        "decisao", decidida.statusAprovacao().name(),
                        "empenhoId", liquidacao.empenhoId().valor().toString())));

        return decidida;
    }
}
