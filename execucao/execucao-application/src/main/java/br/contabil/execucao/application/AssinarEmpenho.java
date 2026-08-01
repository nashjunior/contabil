package br.contabil.execucao.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import br.contabil.execucao.domain.DocumentoAssinadoEmpenho;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoAssinaturaConflitanteException;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.StatusEmpenho;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.assinatura.NivelAssinaturaExigidoPort;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.CertificadoInvalidoException;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.DocumentoAssinado;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.DocumentoParaAssinar;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelAssinatura;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.ReferenciaDocumento;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.Signatario;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: assinatura eletrônica da nota de empenho (ADR-0027 (c)) —
 * interativo e síncrono na requisição do ordenador já autenticado via OAuth2
 * gov.br (RAZ-28/29), não outbox/worker. Exige {@code Acao.ASSINAR} sobre
 * {@code execucao:empenho} (RBAC+MFA, ADR-0016).
 *
 * <p>Idempotência do ato interativo (R3): a transição {@code
 * PENDENTE_ASSINATURA|ASSINATURA_REJEITADA -> ASSINADO} só acontece por update
 * condicional no repositório — duplo-clique ou retry pós-timeout do gov.br vira
 * {@link EmpenhoAssinaturaConflitanteException} (409), nunca uma segunda
 * assinatura. Este caso de uso nunca toca o razão (R1/R2): o comprometimento
 * contábil já é efetivo desde {@code RegistrarEmpenho}.
 */
public class AssinarEmpenho {

    private static final Recurso RECURSO_EMPENHO = new Recurso("execucao:empenho");
    private static final String TIPO_DOCUMENTO = "nota_empenho";

    private final ControleAcesso controleAcesso;
    private final EmpenhoRepository repositorio;
    private final NivelAssinaturaExigidoPort nivelAssinaturaExigido;
    private final ServicoAssinatura servicoAssinatura;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public AssinarEmpenho(
            ControleAcesso controleAcesso,
            EmpenhoRepository repositorio,
            NivelAssinaturaExigidoPort nivelAssinaturaExigido,
            ServicoAssinatura servicoAssinatura,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.controleAcesso = controleAcesso;
        this.repositorio = repositorio;
        this.nivelAssinaturaExigido = nivelAssinaturaExigido;
        this.servicoAssinatura = servicoAssinatura;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public Empenho executar(Sessao usuarioAutenticado, TenantId enteId, EmpenhoId empenhoId) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_EMPENHO, Acao.ASSINAR);

        Empenho empenho = repositorio
                .buscarPorId(enteId, empenhoId)
                .orElseThrow(() -> new ExecucaoInvalidaException(
                        "empenho_nao_encontrado", "empenho %s não encontrado para o ente".formatted(empenhoId)));
        exigirPendenteDeAssinatura(empenho);

        NivelAssinatura nivel = nivelAssinaturaExigido.nivelExigido(enteId, TIPO_DOCUMENTO);
        ReferenciaDocumento origem = new ReferenciaDocumento(empenho.documentoPendenteUri().orElseThrow(
                () -> new IllegalStateException(
                        "empenho %s em %s sem documentoPendenteUri".formatted(empenhoId, empenho.status()))));
        DocumentoParaAssinar documento = new DocumentoParaAssinar(enteId, origem, TIPO_DOCUMENTO);
        // Sessao (ADR-0015) não carrega nome do titular (minimização de PII) — só CPF,
        // única identidade nominal disponível para compor o Signatario aqui.
        String cpfOrdenador = usuarioAutenticado.titular().numero();
        String cpfOrdenadorMascarado = usuarioAutenticado.titular().mascarado();
        List<Signatario> signatarios = List.of(new Signatario(cpfOrdenador, cpfOrdenadorMascarado));

        DocumentoAssinado resultado;
        try {
            resultado = servicoAssinatura.assinar(documento, nivel, signatarios);
        } catch (CertificadoInvalidoException e) {
            // marcarAssinaturaRejeitada também é a transição condicional (WHERE status =
            // pendente_assinatura, R3): se perdeu a corrida contra uma assinatura concorrente
            // que já efetivou ASSINADO, não grava trilha de rejeição sobre um estado que não
            // é mais o real — mas a exceção do provedor ainda é verdadeira para ESTA chamada.
            if (repositorio.marcarAssinaturaRejeitada(enteId, empenhoId)) {
                auditoria.append(new EventoAuditoria(
                        enteId,
                        "execucao_empenho_assinatura_rejeitada",
                        cpfOrdenadorMascarado,
                        "execucao:empenho:%s".formatted(empenhoId.valor()),
                        Instant.now(clock),
                        Map.of("motivo", Cpf.mascararOcorrencias(String.valueOf(e.getMessage())))));
            }
            throw e;
        }

        DocumentoAssinadoEmpenho documentoAssinado = new DocumentoAssinadoEmpenho(
                resultado.pdfAssinado().uri(),
                resultado.hash(),
                resultado.manifesto(),
                resultado.idTransacao(),
                nivel,
                usuarioAutenticado.titular(),
                Instant.now(clock));

        boolean transicionou = repositorio.marcarAssinado(enteId, empenhoId, documentoAssinado);
        if (!transicionou) {
            throw new EmpenhoAssinaturaConflitanteException(
                    "empenho %s não está mais pendente de assinatura (duplo-clique/retry)".formatted(empenhoId));
        }

        auditoria.append(new EventoAuditoria(
                enteId,
                "execucao_empenho_assinado",
                cpfOrdenadorMascarado,
                "execucao:empenho:%s".formatted(empenhoId.valor()),
                Instant.now(clock),
                Map.of("idTransacao", resultado.idTransacao().toString(), "nivel", nivel.name())));

        return empenho.assinar(documentoAssinado);
    }

    private void exigirPendenteDeAssinatura(Empenho empenho) {
        if (empenho.status() != StatusEmpenho.PENDENTE_ASSINATURA
                && empenho.status() != StatusEmpenho.ASSINATURA_REJEITADA) {
            throw new EmpenhoAssinaturaConflitanteException(
                    "empenho %s está em status %s, não está pendente de assinatura"
                            .formatted(empenho.id().valor(), empenho.status()));
        }
    }
}
