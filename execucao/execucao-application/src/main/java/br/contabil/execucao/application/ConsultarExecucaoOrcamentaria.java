package br.contabil.execucao.application;

import java.util.Objects;

import br.contabil.execucao.domain.ExecucaoOrcamentariaPeriodo;
import br.contabil.execucao.domain.repository.ExecucaoOrcamentariaPeriodoPort;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: consulta a execução orçamentária (empenhado/liquidado/pago/saldo a
 * pagar) de um ente, acumulada até um período (RAZ-97, RAZ-84). Mesmo padrão de
 * {@code razao.ConsultarSaldo}: {@code executar(..)} cai no advisor de
 * tenant/transação de {@code TenantContextUseCasesConfiguration} e ganha
 * {@link ControleAcesso} de graça. {@link Acao#LER} nunca exige MFA (RAZ-33).
 */
public class ConsultarExecucaoOrcamentaria {

    private static final Recurso RECURSO_EXECUCAO_ORCAMENTARIA = new Recurso("execucao:orcamento");

    private final ControleAcesso controleAcesso;
    private final ExecucaoOrcamentariaPeriodoPort execucaoOrcamentaria;

    public ConsultarExecucaoOrcamentaria(
            ControleAcesso controleAcesso, ExecucaoOrcamentariaPeriodoPort execucaoOrcamentaria) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.execucaoOrcamentaria = Objects.requireNonNull(execucaoOrcamentaria, "execucaoOrcamentaria");
    }

    public ExecucaoOrcamentariaPeriodo executar(Sessao usuarioAutenticado, TenantId enteId, int exercicio, int mes) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_EXECUCAO_ORCAMENTARIA, Acao.LER);
        return execucaoOrcamentaria.execucaoDoPeriodo(enteId, exercicio, mes);
    }
}
