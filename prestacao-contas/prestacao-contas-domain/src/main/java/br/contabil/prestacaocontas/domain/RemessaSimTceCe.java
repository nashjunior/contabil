package br.contabil.prestacaocontas.domain;

import java.util.Base64;
import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;

/** Artefato PGI/ZIP pronto para entrega assistida no SIMWEB. */
public record RemessaSimTceCe(
        TenantId enteId,
        int exercicio,
        int mes,
        String tabela,
        String nomeArquivoPgi,
        byte[] conteudoPgi,
        String nomeArquivoZip,
        byte[] conteudoZip) {

    public RemessaSimTceCe {
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(tabela, "tabela");
        Objects.requireNonNull(nomeArquivoPgi, "nomeArquivoPgi");
        Objects.requireNonNull(conteudoPgi, "conteudoPgi");
        Objects.requireNonNull(nomeArquivoZip, "nomeArquivoZip");
        Objects.requireNonNull(conteudoZip, "conteudoZip");
        conteudoPgi = conteudoPgi.clone();
        conteudoZip = conteudoZip.clone();
    }

    @Override
    public byte[] conteudoPgi() {
        return conteudoPgi.clone();
    }

    @Override
    public byte[] conteudoZip() {
        return conteudoZip.clone();
    }

    public String conteudoZipBase64() {
        return Base64.getEncoder().encodeToString(conteudoZip);
    }
}
