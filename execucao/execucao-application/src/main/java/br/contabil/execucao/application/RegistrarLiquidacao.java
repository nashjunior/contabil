package br.contabil.execucao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import br.contabil.execucao.domain.DocumentoSuporte;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort.SolicitacaoEscrituracaoLiquidacao;
import br.contabil.execucao.domain.repository.LiquidacaoRepository;
import br.contabil.execucao.domain.repository.SaldosExecucaoPort;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: registra uma liquidação vinculada a um empenho.
 *
 * <p>Fluxo: RBAC/MFA -> valida documento/valor -> consulta saldo do empenho ->
 * escritura o fato no razão por port -> persiste a liquidação já amarrada ao
 * fato. A transação é responsabilidade da borda/infra.
 */
public class RegistrarLiquidacao {

    private static final Recurso RECURSO_LIQUIDACAO = new Recurso("execucao:liquidacao");

    private final ControleAcesso controleAcesso;
    private final SaldosExecucaoPort saldos;
    private final ExecucaoContabilPort escrituracao;
    private final LiquidacaoRepository repositorio;
    private final PublicacaoTransparenciaExecucaoPort publicacaoTransparencia;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public RegistrarLiquidacao(
            ControleAcesso controleAcesso,
            SaldosExecucaoPort saldos,
            ExecucaoContabilPort escrituracao,
            LiquidacaoRepository repositorio,
            PublicacaoTransparenciaExecucaoPort publicacaoTransparencia,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.controleAcesso = controleAcesso;
        this.saldos = saldos;
        this.escrituracao = escrituracao;
        this.repositorio = repositorio;
        this.publicacaoTransparencia = Objects.requireNonNull(publicacaoTransparencia, "publicação transparência");
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public Liquidacao executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            EmpenhoId empenhoId,
            LocalDate dataCompetencia,
            Dinheiro valor,
            List<DocumentoSuporte> documentosSuporte,
            String historico) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_LIQUIDACAO, Acao.CRIAR);

        Liquidacao.validarEntrada(valor, documentosSuporte, historico);
        var saldoEmpenho = saldos.saldoEmpenho(enteId, empenhoId);
        saldoEmpenho.exigirSaldoParaLiquidar(valor);

        LiquidacaoId liquidacaoId = LiquidacaoId.novo();
        UUID fatoContabilId = escrituracao.registrarLiquidacao(new SolicitacaoEscrituracaoLiquidacao(
                enteId, liquidacaoId, empenhoId, dataCompetencia, valor, documentosSuporte, historico));

        Liquidacao liquidacao = Liquidacao.registrar(
                liquidacaoId,
                enteId,
                empenhoId,
                dataCompetencia,
                valor,
                documentosSuporte,
                historico,
                fatoContabilId,
                usuarioAutenticado.titular());
        repositorio.inserir(liquidacao);
        publicacaoTransparencia.publicar(liquidacao, usuarioAutenticado);
        auditoria.append(new EventoAuditoria(
                enteId,
                "execucao_liquidacao_registrada",
                usuarioAutenticado.titular().numero(),
                "execucao:liquidacao:%s".formatted(liquidacao.id().valor()),
                Instant.now(clock),
                Map.of(
                        "empenhoId", empenhoId.valor().toString(),
                        "fatoContabilId", fatoContabilId.toString(),
                        "valor", valor.valor().toPlainString())));
        return liquidacao;
    }
}
