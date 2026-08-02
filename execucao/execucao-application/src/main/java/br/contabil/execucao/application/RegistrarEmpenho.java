package br.contabil.execucao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

import br.contabil.execucao.domain.ContratoId;
import br.contabil.execucao.domain.CredorId;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.ContadorEmpenhoPort;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort.SolicitacaoEscrituracaoEmpenho;
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
 * Caso de uso: registra um empenho, comprometendo crédito da dotação (Lei
 * 4.320/1964 art. 58-60).
 *
 * <p>Fluxo: RBAC/MFA -> valida valor -> gate art. 42 da LRF (RAZ-243/ADR-0044,
 * só bloqueia nos 2 últimos quadrimestres do mandato) -> consulta saldo da
 * dotação (art. 59) -> escritura o fato no razão por port -> persiste o
 * empenho já amarrado ao fato. A transação é responsabilidade da borda/infra.
 */
public class RegistrarEmpenho {

    private static final Recurso RECURSO_EMPENHO = new Recurso("execucao:empenho");

    private final ControleAcesso controleAcesso;
    private final SaldosExecucaoPort saldos;
    private final ExecucaoContabilPort escrituracao;
    private final ContadorEmpenhoPort contadorEmpenho;
    private final EmpenhoRepository repositorio;
    private final PublicacaoTransparenciaExecucaoPort publicacaoTransparencia;
    private final SolicitacaoDocumentoAssinaturaPort solicitacaoDocumentoAssinatura;
    private final VerificarDisponibilidadeArt42 verificarDisponibilidadeArt42;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public RegistrarEmpenho(
            ControleAcesso controleAcesso,
            SaldosExecucaoPort saldos,
            ExecucaoContabilPort escrituracao,
            ContadorEmpenhoPort contadorEmpenho,
            EmpenhoRepository repositorio,
            PublicacaoTransparenciaExecucaoPort publicacaoTransparencia,
            SolicitacaoDocumentoAssinaturaPort solicitacaoDocumentoAssinatura,
            VerificarDisponibilidadeArt42 verificarDisponibilidadeArt42,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.controleAcesso = controleAcesso;
        this.saldos = saldos;
        this.escrituracao = escrituracao;
        this.contadorEmpenho = contadorEmpenho;
        this.repositorio = repositorio;
        this.publicacaoTransparencia = Objects.requireNonNull(publicacaoTransparencia, "publicação transparência");
        this.solicitacaoDocumentoAssinatura =
                Objects.requireNonNull(solicitacaoDocumentoAssinatura, "solicitação documento assinatura");
        this.verificarDisponibilidadeArt42 =
                Objects.requireNonNull(verificarDisponibilidadeArt42, "verificação disponibilidade art. 42");
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public Empenho executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            DotacaoId dotacaoId,
            TipoEmpenho tipo,
            CredorId credorId,
            UnidadeGestoraId unidadeGestoraId,
            ContratoId contratoId,
            Dinheiro valor,
            LocalDate dataFato,
            int exercicio,
            String classificacaoOrcamentaria,
            String fonteRecurso,
            String historico) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_EMPENHO, Acao.CRIAR);

        Empenho.validarValor(valor, "valor do empenho");
        verificarDisponibilidadeArt42.verificar(enteId, dataFato, fonteRecurso, valor);

        var saldoDotacao = saldos.saldoDotacao(enteId, dotacaoId);
        saldoDotacao.exigirSaldoParaComprometer(valor);

        EmpenhoId empenhoId = EmpenhoId.novo();
        ReferenciaFatoContabil fatoContabilId = escrituracao.registrarEmpenho(
                new SolicitacaoEscrituracaoEmpenho(enteId, empenhoId, dataFato, valor, historico, fonteRecurso));
        long numeroSequencial = contadorEmpenho.proximoNumero(enteId, exercicio);

        Empenho empenho = Empenho.registrar(
                empenhoId,
                enteId,
                numeroSequencial,
                exercicio,
                tipo,
                dotacaoId,
                credorId,
                unidadeGestoraId,
                contratoId,
                valor,
                dataFato,
                classificacaoOrcamentaria,
                fonteRecurso,
                historico,
                fatoContabilId,
                usuarioAutenticado.titular());

        repositorio.inserir(empenho);
        publicacaoTransparencia.publicar(empenho, usuarioAutenticado);
        solicitacaoDocumentoAssinatura.solicitar(empenho, usuarioAutenticado);

        auditoria.append(new EventoAuditoria(
                enteId,
                "execucao_empenho_registrado",
                usuarioAutenticado.titular().mascarado(),
                "execucao:empenho:%s".formatted(empenho.id().valor()),
                Instant.now(clock),
                Map.of(
                        "dotacaoId", dotacaoId.valor().toString(),
                        "fatoContabilId", fatoContabilId.valor().toString(),
                        "valor", valor.valor().toPlainString())));
        return empenho;
    }
}
