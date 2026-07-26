package br.contabil.execucao.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelAssinatura;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;

/**
 * {@code DOCUMENTO_ASSINADO} da nota de empenho (ADR-0027 (c)): resultado de
 * {@code ServicoAssinatura#assinar}, persistido com FK ao {@link Empenho} pela
 * transição {@link Empenho#assinar}. Só a URI/hash/manifesto/id de transação são
 * guardados — o binário assinado vive no object store (ADR-0009).
 */
public record DocumentoAssinadoEmpenho(
        URI pdfAssinado,
        String hashSha256,
        String manifesto,
        UUID idTransacao,
        NivelAssinatura nivel,
        Cpf signatario,
        Instant assinadoEm) {

    public DocumentoAssinadoEmpenho {
        Objects.requireNonNull(pdfAssinado, "pdfAssinado não pode ser nulo");
        Objects.requireNonNull(hashSha256, "hashSha256 não pode ser nulo");
        Objects.requireNonNull(manifesto, "manifesto não pode ser nulo");
        Objects.requireNonNull(idTransacao, "idTransacao não pode ser nulo");
        Objects.requireNonNull(nivel, "nivel não pode ser nulo");
        Objects.requireNonNull(signatario, "signatario não pode ser nulo");
        Objects.requireNonNull(assinadoEm, "assinadoEm não pode ser nulo");
    }
}
