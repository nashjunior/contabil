package br.contabil.execucao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import br.contabil.execucao.domain.Beneficiario;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.NaturezaPagamento;
import br.contabil.execucao.domain.Pagamento;
import br.contabil.execucao.domain.PagamentoId;
import br.contabil.execucao.domain.PagamentoNaoAprovadoException;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.StatusAprovacao;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort.SolicitacaoEscrituracaoPagamento;
import br.contabil.execucao.domain.repository.IdempotenciaPagamentoRepository;
import br.contabil.execucao.domain.repository.LiquidacaoRepository;
import br.contabil.execucao.domain.repository.PagamentoRepository;
import br.contabil.execucao.domain.repository.SaldosExecucaoPort;
import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: registra pagamento total ou parcial de uma liquidação.
 *
 * <p>O pagamento nunca altera a liquidação anterior; ele cria um novo estágio e
 * um novo fato contábil. Entrega bancária/assinatura, quando aplicável, deve ser
 * agendada por outbox na infra, fora da transação síncrona com sistemas externos.
 *
 * <p>Idempotência ponta a ponta (ADR-0011/RAZ-134): quando o chamador informa
 * {@code chaveIdempotencia}, a reserva acontece ANTES da validação de
 * liquidação/saldo — um reenvio com a mesma chave devolve o pagamento
 * original sem repetir o efeito, mesmo que o saldo já tenha sido consumido
 * pela primeira execução.
 */
public class RegistrarPagamento {

    private static final Recurso RECURSO_PAGAMENTO = new Recurso("execucao:pagamento");

    private final ControleAcesso controleAcesso;
    private final LiquidacaoRepository liquidacaoRepositorio;
    private final SaldosExecucaoPort saldos;
    private final ExecucaoContabilPort escrituracao;
    private final PagamentoRepository repositorio;
    private final IdempotenciaPagamentoRepository idempotencia;
    private final PublicacaoTransparenciaExecucaoPort publicacaoTransparencia;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public RegistrarPagamento(
            ControleAcesso controleAcesso,
            LiquidacaoRepository liquidacaoRepositorio,
            SaldosExecucaoPort saldos,
            ExecucaoContabilPort escrituracao,
            PagamentoRepository repositorio,
            IdempotenciaPagamentoRepository idempotencia,
            PublicacaoTransparenciaExecucaoPort publicacaoTransparencia,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.controleAcesso = controleAcesso;
        this.liquidacaoRepositorio = liquidacaoRepositorio;
        this.saldos = saldos;
        this.escrituracao = escrituracao;
        this.repositorio = repositorio;
        this.idempotencia = idempotencia;
        this.publicacaoTransparencia = Objects.requireNonNull(publicacaoTransparencia, "publicação transparência");
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public Pagamento executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            LiquidacaoId liquidacaoId,
            LocalDate dataCompetencia,
            Dinheiro valor,
            NaturezaPagamento natureza,
            Optional<Beneficiario> beneficiario,
            Optional<String> ordemBancaria,
            String historico,
            Optional<ChaveIdempotencia> chaveIdempotencia) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_PAGAMENTO, Acao.CRIAR);

        Pagamento.validarEntrada(valor, natureza, beneficiario, ordemBancaria, historico);

        PagamentoId pagamentoId = PagamentoId.novo();
        if (chaveIdempotencia.isPresent()) {
            PagamentoId idEfetivo = idempotencia.reservar(enteId, chaveIdempotencia.get(), pagamentoId);
            if (!idEfetivo.equals(pagamentoId)) {
                return repositorio
                        .buscarPorId(enteId, idEfetivo)
                        .orElseThrow(() -> new IllegalStateException(
                                "idempotência: pagamento %s referenciado pela chave não encontrado"
                                        .formatted(idEfetivo)));
            }
        }

        Liquidacao liquidacao = liquidacaoRepositorio
                .buscarPorId(enteId, liquidacaoId)
                .orElseThrow(() -> new ExecucaoInvalidaException(
                        "liquidacao_nao_encontrada", "liquidação %s não encontrada para o ente".formatted(liquidacaoId)));
        if (liquidacao.statusAprovacao() != StatusAprovacao.APROVADA) {
            throw new PagamentoNaoAprovadoException(liquidacaoId, liquidacao.statusAprovacao());
        }

        var saldoLiquidacao = saldos.saldoLiquidacao(enteId, liquidacaoId);
        saldoLiquidacao.exigirSaldoParaPagar(valor);

        ReferenciaFatoContabil fatoContabilId = escrituracao.registrarPagamento(new SolicitacaoEscrituracaoPagamento(
                enteId,
                pagamentoId,
                liquidacaoId,
                dataCompetencia,
                valor,
                natureza,
                beneficiario,
                ordemBancaria,
                historico));

        Pagamento pagamento = Pagamento.registrar(
                pagamentoId,
                enteId,
                liquidacaoId,
                dataCompetencia,
                valor,
                natureza,
                beneficiario,
                ordemBancaria,
                historico,
                fatoContabilId.valor());
        repositorio.inserir(pagamento);
        publicacaoTransparencia.publicar(pagamento, usuarioAutenticado);
        auditoria.append(new EventoAuditoria(
                enteId,
                "execucao_pagamento_registrado",
                usuarioAutenticado.titular().mascarado(),
                "execucao:pagamento:%s".formatted(pagamento.id().valor()),
                Instant.now(clock),
                Map.of(
                        "liquidacaoId", liquidacaoId.valor().toString(),
                        "fatoContabilId", fatoContabilId.valor().toString(),
                        "natureza", natureza.name(),
                        "valor", valor.valor().toPlainString())));
        return pagamento;
    }
}
