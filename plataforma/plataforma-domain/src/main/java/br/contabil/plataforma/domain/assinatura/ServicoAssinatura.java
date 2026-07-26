package br.contabil.plataforma.domain.assinatura;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import br.contabil.plataforma.domain.ErroContrato;
import br.contabil.plataforma.domain.TenantId;

/**
 * Contrato do serviço de <b>Assinatura eletrônica</b> (doc 11 §Contratos;
 * ADR-0008 abstração de provedor; ADR-0009 documento em object store; ref
 * transversais/01-assinatura-eletronica, Lei 14.063/2020 · MP 2.200-2).
 *
 * <p>Assinatura por <b>abstração de provedor</b> (gov.br avançada em F0, ICP-Brasil
 * qualificada em F1) com saída <b>PAdES/PDF</b>. O documento não trafega como BLOB:
 * a entrada e a saída referenciam o <b>object store/GED</b> por URI (ADR-0009); o
 * banco guarda apenas metadados (hash, id de transação, uri).
 *
 * <p>A disponibilidade do provedor externo é tratada como estado "pendente de
 * assinatura", nunca resultado parcial: uma falha lança exceção, não devolve um
 * documento meio-assinado.
 */
public interface ServicoAssinatura {

    /**
     * Assina {@code documento} no {@code nivel} exigido, para os {@code signatarios}.
     *
     * @throws CertificadoInvalidoException certificado/credencial de assinatura inválido
     * @throws NivelInsuficienteException   nível oferecido abaixo do exigido pelo documento
     */
    DocumentoAssinado assinar(DocumentoParaAssinar documento, NivelAssinatura nivel, List<Signatario> signatarios);

    /** Verifica a validade jurídica de um documento assinado (validação delegada ao ITI). */
    ResultadoVerificacao verificar(ReferenciaDocumento documento);

    // ---- Value objects do contrato --------------------------------------------

    /** Nível de assinatura conforme Lei 14.063/2020 (ADR-0008). */
    enum NivelAssinatura {
        AVANCADA_GOVBR,
        QUALIFICADA_ICP_BRASIL
    }

    /** Referência ao documento no object store/GED — nunca o binário em si (ADR-0009). */
    record ReferenciaDocumento(URI uri) {
        public ReferenciaDocumento {
            Objects.requireNonNull(uri, "uri do documento");
        }
    }

    /**
     * Documento a assinar: o <b>ente</b> dono do dado (atribuição de tenant explícita
     * no contrato — ADR-0015; convenção da casa: o campo é {@code ente}, tipado
     * {@link TenantId}), a origem no object store e o tipo de negócio (empenho,
     * contrato, OB).
     */
    record DocumentoParaAssinar(TenantId ente, ReferenciaDocumento origem, String tipoDocumento) {
        public DocumentoParaAssinar {
            Objects.requireNonNull(ente, "ente do documento");
            Objects.requireNonNull(origem, "origem do documento");
            Objects.requireNonNull(tipoDocumento, "tipo do documento");
        }
    }

    /** Signatário identificado por CPF (identidade nominal, LGPD/segregação de funções). */
    record Signatario(String cpf, String nome) {
        public Signatario {
            Objects.requireNonNull(cpf, "cpf do signatário");
            Objects.requireNonNull(nome, "nome do signatário");
        }
    }

    /** Saída da assinatura: PDF assinado (PAdES) referenciado + manifesto, hash e id de transação. */
    record DocumentoAssinado(
            ReferenciaDocumento pdfAssinado,
            String manifesto,
            String hash,
            UUID idTransacao) {
        public DocumentoAssinado {
            Objects.requireNonNull(pdfAssinado, "pdf assinado");
            Objects.requireNonNull(manifesto, "manifesto de assinatura");
            Objects.requireNonNull(hash, "hash do documento");
            Objects.requireNonNull(idTransacao, "id da transação de assinatura");
        }
    }

    /** Resultado da verificação de um documento assinado. */
    record ResultadoVerificacao(boolean valido, String detalhe) {
        public ResultadoVerificacao {
            Objects.requireNonNull(detalhe, "detalhe da verificação");
        }
    }

    // ---- Erros do contrato (doc 11 §Contratos) --------------------------------

    /** Erro {@code certificado_invalido}: certificado/credencial de assinatura inválido. */
    final class CertificadoInvalidoException extends RuntimeException implements ErroContrato {
        public CertificadoInvalidoException(String mensagem) {
            super(mensagem);
        }

        @Override
        public String codigo() {
            return "certificado_invalido";
        }
    }

    /** Erro {@code nivel_insuficiente}: nível de assinatura abaixo do exigido pelo documento. */
    final class NivelInsuficienteException extends RuntimeException implements ErroContrato {
        public NivelInsuficienteException(String mensagem) {
            super(mensagem);
        }

        @Override
        public String codigo() {
            return "nivel_insuficiente";
        }
    }
}
