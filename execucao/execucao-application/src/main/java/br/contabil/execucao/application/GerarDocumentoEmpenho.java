package br.contabil.execucao.application;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.GeradorNotaEmpenhoPdf;
import br.contabil.execucao.domain.StatusEmpenho;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos.DocumentoJaExistenteException;

/**
 * Processa (ADR-0027 (b)) o evento {@code execucao_empenho_pendente_documento}
 * publicado por {@link RegistrarEmpenho}: gera o PDF/A da nota a partir do
 * agregado, publica no object store (ADR-0009) e transiciona o {@link Empenho}
 * para {@code PENDENTE_ASSINATURA}.
 *
 * <p>Acionado pelo worker de outbox ({@code EmpenhoDocumentoPendenteWorker}), não
 * por um usuário interativo — sem {@code Sessao}/RBAC (a autorização já ocorreu em
 * {@code RegistrarEmpenho}); declara {@link TenantId} para o advisor de tenant
 * (RAZ-21) setar {@code app.ente_id} na mesma transação que o advisor de
 * transação (RAZ-14) abre para este {@code executar(..)}.
 *
 * <p><b>Idempotente</b> pela chave do outbox (ADR-0011): reprocessar o mesmo
 * evento não gera um segundo PDF — nem porque o {@code status} já saiu de
 * {@code REGISTRADO} (no-op cedo), nem porque a URI de destino é determinística e
 * a escrita condicional no object store também trata
 * {@link DocumentoJaExistenteException} como sucesso (retry pós-crash).
 */
public class GerarDocumentoEmpenho {

    private final EmpenhoRepository repositorio;
    private final GeradorNotaEmpenhoPdf gerador;
    private final ArmazenamentoDocumentos armazenamento;
    private final String bucketDocumentos;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public GerarDocumentoEmpenho(
            EmpenhoRepository repositorio,
            GeradorNotaEmpenhoPdf gerador,
            ArmazenamentoDocumentos armazenamento,
            String bucketDocumentos,
            AuditoriaEscrita auditoria,
            Clock clock) {
        this.repositorio = repositorio;
        this.gerador = gerador;
        this.armazenamento = armazenamento;
        this.bucketDocumentos = textoObrigatorio(bucketDocumentos);
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public void executar(TenantId enteId, EmpenhoId empenhoId) {
        Empenho empenho = repositorio
                .buscarPorId(enteId, empenhoId)
                .orElseThrow(() -> new ExecucaoInvalidaException(
                        "empenho_nao_encontrado", "empenho %s não encontrado para o ente".formatted(empenhoId)));

        if (empenho.status() != StatusEmpenho.REGISTRADO) {
            return; // reprocessamento idempotente: outra entrega já avançou o documento
        }

        byte[] pdf = gerador.gerar(empenho);
        URI destino = uriDocumentoPendente(enteId, empenhoId);
        URI uriArmazenada;
        try {
            uriArmazenada = armazenamento.armazenar(pdf, destino);
        } catch (DocumentoJaExistenteException e) {
            uriArmazenada = destino; // retry pós-crash: o PDF já foi publicado numa tentativa anterior
        }

        boolean transicionou = repositorio.marcarPendenteAssinatura(enteId, empenhoId, uriArmazenada);
        if (!transicionou) {
            return; // corrida perdida contra outra entrega do mesmo evento — nada a fazer
        }

        auditoria.append(new EventoAuditoria(
                enteId,
                "execucao_empenho_documento_gerado",
                "worker:empenho-documento-pendente",
                "execucao:empenho:%s".formatted(empenhoId.valor()),
                Instant.now(clock),
                Map.of("documentoPendenteUri", uriArmazenada.toString())));
    }

    private URI uriDocumentoPendente(TenantId enteId, EmpenhoId empenhoId) {
        return URI.create(
                "s3://%s/%s/execucao/empenho/%s/nota-empenho.pdf"
                        .formatted(bucketDocumentos, enteId.valor(), empenhoId.valor()));
    }

    private static String textoObrigatorio(String valor) {
        Objects.requireNonNull(valor, "bucketDocumentos não pode ser nulo");
        if (valor.isBlank()) {
            throw new IllegalArgumentException("bucketDocumentos não pode ser vazio");
        }
        return valor;
    }
}
