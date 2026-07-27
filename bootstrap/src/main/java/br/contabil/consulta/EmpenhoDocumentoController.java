package br.contabil.consulta;

import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.contabil.execucao.application.ConsultarDocumentoEmpenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * {@code GET /api/v1/entes/{enteId}/execucao/empenhos/{id}/documento} (ADR-0039
 * decisão 2, ratificada em RAZ-152/§6.10, RAZ-157): bridge HTTP fina para {@code
 * ArmazenamentoDocumentos.ler(URI)} (ADR-0009) — sem ela a pré-visualização do
 * PDF na tela de assinatura (RAZ-141) é maquete, porque {@code
 * Empenho.documentoPendenteUri()}/{@code DocumentoAssinadoEmpenho.pdfAssinado()}
 * são estado interno do domínio. O controller RESOLVE a URI a partir do
 * agregado (nunca aceita URI do cliente — guardiao-arquitetura, mesma borda
 * fina de {@link ExecucaoConsultaController}).
 *
 * <p>Separado de {@link ExecucaoConsultaController} porque {@link
 * ConsultarDocumentoEmpenho} só existe quando o object store está configurado
 * (mesma razão de {@code AssinaturaEmpenhoController} usar {@link
 * ObjectProvider} em vez de injeção direta) — as demais consultas daquele
 * controller nunca dependem disso, e um bean condicional ausente não pode
 * quebrar o wiring do controller inteiro.
 */
@RestController
@RequestMapping("/api/v1/entes/{enteId}/execucao/empenhos")
final class EmpenhoDocumentoController {

    private final ObjectProvider<ConsultarDocumentoEmpenho> consultarDocumentoEmpenho;

    EmpenhoDocumentoController(ObjectProvider<ConsultarDocumentoEmpenho> consultarDocumentoEmpenho) {
        this.consultarDocumentoEmpenho =
                Objects.requireNonNull(consultarDocumentoEmpenho, "consultarDocumentoEmpenho");
    }

    /**
     * Sem {@code produces} fixo: erros (404 {@code documento_nao_encontrado}, etc.)
     * são JSON pelo mesmo {@code ErroContratoExceptionHandler} dos demais
     * controllers — restringir a {@code application/pdf} aqui faria o Spring
     * devolver {@code 406} cru (sem o envelope §6.1) para clientes que pedem
     * {@code Accept: application/json}, antes mesmo do handler rodar.
     */
    @GetMapping("/{id}/documento")
    ResponseEntity<byte[]> documento(
            @PathVariable("enteId") UUID enteId, @PathVariable("id") UUID id, Sessao sessao) {
        ConsultarDocumentoEmpenho useCase = consultarDocumentoEmpenho.getIfAvailable();
        if (useCase == null) {
            throw new IllegalStateException(
                    "armazenamento de documentos não configurado (contabil.objectstore.enabled)");
        }
        byte[] pdf = useCase.executar(sessao, new TenantId(enteId), new EmpenhoId(id));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }
}
