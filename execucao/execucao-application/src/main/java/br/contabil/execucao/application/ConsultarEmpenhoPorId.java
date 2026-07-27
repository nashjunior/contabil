package br.contabil.execucao.application;

import java.util.Objects;

import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.EmpenhoNaoEncontradoException;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: leitura do empenho por id (ADR-0039 decisão 1, ratificada em
 * RAZ-152/§6.10) — devolve o agregado completo (status + estado do documento);
 * montar o bloco {@code documento} do contrato de leitura (nunca vazando a
 * s3:// crua, R4) é responsabilidade do controller, não deste caso de uso.
 * {@link Acao#LER} sobre {@code execucao:empenho}, sem MFA (mesma classe de
 * leitura de {@link ConsultarEmpenhosRegistrados}/{@link ConsultarDocumentoEmpenho}).
 */
public class ConsultarEmpenhoPorId {

    private static final Recurso RECURSO_EMPENHO = new Recurso("execucao:empenho");

    private final ControleAcesso controleAcesso;
    private final EmpenhoRepository repositorio;

    public ConsultarEmpenhoPorId(ControleAcesso controleAcesso, EmpenhoRepository repositorio) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
    }

    public Empenho executar(Sessao usuarioAutenticado, TenantId enteId, EmpenhoId empenhoId) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_EMPENHO, Acao.LER);

        return repositorio
                .buscarPorId(enteId, empenhoId)
                .orElseThrow(() -> new EmpenhoNaoEncontradoException(
                        "empenho %s não encontrado para o ente %s".formatted(empenhoId.valor(), enteId.valor())));
    }
}
