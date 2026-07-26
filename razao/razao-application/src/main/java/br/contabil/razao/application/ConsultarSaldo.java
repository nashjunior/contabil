package br.contabil.razao.application;

import java.util.Objects;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.ContaNaoEncontradaException;
import br.contabil.razao.domain.repository.CatalogoContasPort;
import br.contabil.razao.domain.repository.ConsultaSaldoPort;

/**
 * Caso de uso: consulta o saldo devedor líquido de uma conta (motor-razao-partidas-dobradas.md,
 * ADR-0007 — read-model derivado dos lançamentos).
 *
 * <p>RAZ-59: {@link ConsultaSaldoPort} não tinha use case próprio em {@code razao-application} —
 * chamado direto, fica fora do pointcut de {@code TenantContextUseCasesConfiguration}
 * ({@code execution(* br.contabil..application..*.executar(..))}), então {@code app.ente_id}
 * nunca é setado para essa consulta. Este use case fecha essa lacuna pelo mesmo padrão de
 * {@link RegistrarFatoContabil}/{@link EstornarFatoContabil}: {@code executar(..)} aqui cai no
 * advisor e ganha {@link ControleAcesso} de graça. {@link Acao#LER} nunca exige MFA
 * (RAZ-33, {@code ControleAcesso.MOVIMENTA_RECURSO}).
 *
 * <p>RAZ-117 / ADR-0030 §6 (gap 2): {@code saldo_conta} devolve zero indistintamente para
 * "conta existente sem lançamento" e para "conta inexistente". Antes de ler o saldo, valida a
 * existência da conta no ente via {@link CatalogoContasPort#existe} (mesma {@code conta_pcasp}
 * do catálogo) e lança {@link ContaNaoEncontradaException} (404) quando não existe — assim o
 * zero fica reservado à conta EXISTENTE sem lançamento.
 */
public class ConsultarSaldo {

    private static final Recurso RECURSO_SALDO = new Recurso("razao:saldo_conta");

    private final ControleAcesso controleAcesso;
    private final ConsultaSaldoPort consultaSaldo;
    private final CatalogoContasPort catalogo;

    public ConsultarSaldo(
            ControleAcesso controleAcesso, ConsultaSaldoPort consultaSaldo, CatalogoContasPort catalogo) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.consultaSaldo = Objects.requireNonNull(consultaSaldo, "consultaSaldo");
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
    }

    public Dinheiro executar(Sessao usuarioAutenticado, TenantId enteId, ContaContabilId contaId) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_SALDO, Acao.LER);
        if (!catalogo.existe(enteId, contaId)) {
            throw new ContaNaoEncontradaException(enteId, contaId);
        }
        return consultaSaldo.saldoDevedorLiquido(enteId, contaId);
    }
}
