package br.contabil.razao.application;

import java.util.Objects;
import java.util.Optional;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.PaginaContas;
import br.contabil.razao.domain.repository.CatalogoContasPort;

/**
 * Caso de uso: cataloga/busca contas do PCASP (RAZ-117 / ADR-0030 §6) — o endpoint
 * que faltava para o operador descobrir o {@code contaId} que {@code /razao/saldo}
 * exige. Lista do §6.1 (cursor opaco, {@code limit} default 20/máx 100).
 *
 * <p>Mesmo padrão de {@link ConsultarSaldo} (RAZ-59): {@code executar(..)} cai no
 * advisor de {@code TenantContextUseCasesConfiguration} e ganha {@link ControleAcesso}
 * + {@code app.ente_id} de graça. {@link Acao#LER} nunca exige MFA (RAZ-33).
 * A validação/clamp do {@code limit} é regra de aplicação (não de borda HTTP nem de
 * SQL), por isso mora aqui.
 */
public class ConsultarContas {

    private static final Recurso RECURSO_CONTA = new Recurso("razao:conta_pcasp");
    private static final int LIMITE_PADRAO = 20;
    private static final int LIMITE_MAXIMO = 100;

    private final ControleAcesso controleAcesso;
    private final CatalogoContasPort catalogo;

    public ConsultarContas(ControleAcesso controleAcesso, CatalogoContasPort catalogo) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
    }

    public PaginaContas executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            Optional<String> busca,
            Optional<Integer> limit,
            Optional<String> cursor) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_CONTA, Acao.LER);
        int limite = limit.map(ConsultarContas::clampLimite).orElse(LIMITE_PADRAO);
        return catalogo.buscar(enteId, busca.filter(b -> !b.isBlank()), limite, cursor);
    }

    private static int clampLimite(int solicitado) {
        return Math.max(1, Math.min(solicitado, LIMITE_MAXIMO));
    }
}
