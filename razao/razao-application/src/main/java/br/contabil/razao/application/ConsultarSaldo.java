package br.contabil.razao.application;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.repository.ConsultaSaldoPort;
import java.util.Objects;
import java.util.UUID;

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
 */
public class ConsultarSaldo {

    private static final Recurso RECURSO_SALDO = new Recurso("razao:saldo_conta");

    private final ControleAcesso controleAcesso;
    private final ConsultaSaldoPort consultaSaldo;

    public ConsultarSaldo(ControleAcesso controleAcesso, ConsultaSaldoPort consultaSaldo) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.consultaSaldo = Objects.requireNonNull(consultaSaldo, "consultaSaldo");
    }

    public Dinheiro executar(Sessao usuarioAutenticado, TenantId enteId, UUID contaId) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_SALDO, Acao.LER);
        return consultaSaldo.saldoDevedorLiquido(enteId, contaId);
    }
}
