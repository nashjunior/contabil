package br.contabil.execucao.application;

import java.net.URI;
import java.util.Objects;

import br.contabil.execucao.domain.DocumentoAssinadoEmpenho;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos.DocumentoNaoEncontradoException;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos.DocumentoTenantInvalidoException;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: bridge de leitura do PDF ATUAL do empenho (ADR-0039 decisão 2,
 * ratificada em RAZ-152/§6.10) — o assinado quando {@code ASSINADO}, senão o
 * pendente quando presente. {@link Acao#LER} sobre {@code execucao:empenho},
 * nunca MFA (mesma classe de leitura de {@link ConsultarEmpenhosRegistrados}).
 *
 * <p>O controller nunca aceita URI do cliente (guardiao-arquitetura): a URI
 * lida aqui vem sempre do agregado, resolvido a partir do {@code enteId}
 * verificado (R3). {@code executar(..)} cai no mesmo advisor de tenant/RLS que
 * os demais casos de uso ({@code TenantContextUseCasesConfiguration}), então
 * {@code buscarPorId} já é escopado por RLS; antes de delegar a {@link
 * ArmazenamentoDocumentos#ler}, esta ponte também confere que o prefixo da URI
 * pertence ao {@code enteId} corrente — mesma disciplina de {@code
 * ObjectStoreSeamsAssinaturaConfiguration.validarPrefixoTenant} (RAZ-45) — como
 * defesa em profundidade contra URI cross-tenant persistida por engano; essa
 * checagem nunca é desligada.
 */
public class ConsultarDocumentoEmpenho {

    private static final Recurso RECURSO_EMPENHO = new Recurso("execucao:empenho");

    private final ControleAcesso controleAcesso;
    private final EmpenhoRepository repositorio;
    private final ArmazenamentoDocumentos armazenamento;

    public ConsultarDocumentoEmpenho(
            ControleAcesso controleAcesso, EmpenhoRepository repositorio, ArmazenamentoDocumentos armazenamento) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.armazenamento = Objects.requireNonNull(armazenamento, "armazenamento");
    }

    public byte[] executar(Sessao usuarioAutenticado, TenantId enteId, EmpenhoId empenhoId) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_EMPENHO, Acao.LER);

        Empenho empenho = repositorio
                .buscarPorId(enteId, empenhoId)
                .orElseThrow(() -> new DocumentoNaoEncontradoException(
                        "empenho %s não encontrado para o ente %s".formatted(empenhoId.valor(), enteId.valor())));

        URI documento = empenho.documentoAssinado()
                .map(DocumentoAssinadoEmpenho::pdfAssinado)
                .or(empenho::documentoPendenteUri)
                .orElseThrow(() -> new DocumentoNaoEncontradoException(
                        "empenho %s sem documento pendente ou assinado".formatted(empenhoId.valor())));

        validarPrefixoTenant(enteId, documento);
        return armazenamento.ler(documento);
    }

    /**
     * Verifica que {@code uri} contém o UUID do {@code ente} como primeiro segmento
     * do path ({@code /{ente.valor()}/…}) antes de qualquer acesso ao object store
     * — a s3:// crua nunca vai ao cliente e nenhuma leitura cross-tenant chega a
     * {@link ArmazenamentoDocumentos#ler} (ADR-0009 RAZ-45).
     */
    private static void validarPrefixoTenant(TenantId ente, URI uri) {
        String prefixoEsperado = "/" + ente.valor() + "/";
        if (uri.getPath() == null || !uri.getPath().startsWith(prefixoEsperado)) {
            throw new DocumentoTenantInvalidoException("URI não pertence ao ente " + ente.valor() + ": " + uri);
        }
    }
}
