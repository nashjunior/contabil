package br.contabil.razao.application;

import java.util.List;
import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.LinhaMatrizSaldos;
import br.contabil.razao.domain.repository.MatrizSaldosContabeisPort;

/**
 * Caso de uso: gera a MSC (Matriz de Saldos Contábeis) de um período — o
 * {@link GerarBalancete} estendido pela dimensão das informações complementares
 * (IC), ADR-0056/ADR-0057. Mesmo padrão de {@link GerarBalancete}:
 * {@code executar(..)} cai no advisor de tenant/transação de
 * {@code TenantContextUseCasesConfiguration} e ganha {@link ControleAcesso} de
 * graça; {@link Acao#LER} nunca exige MFA (RAZ-33). Consumido pelo gerador MSC/
 * SICONFI do módulo {@code prestacao-contas} (RAZ-251, ainda não construído) —
 * este use case é o ponto de entrada controlado por RBAC que esse módulo chama,
 * nunca o adapter diretamente.
 */
public class GerarMatrizSaldosContabeis {

    private static final Recurso RECURSO_MATRIZ_SALDOS = new Recurso("razao:matriz_saldos_contabeis");

    private final ControleAcesso controleAcesso;
    private final MatrizSaldosContabeisPort matriz;

    public GerarMatrizSaldosContabeis(ControleAcesso controleAcesso, MatrizSaldosContabeisPort matriz) {
        this.controleAcesso = Objects.requireNonNull(controleAcesso, "controleAcesso");
        this.matriz = Objects.requireNonNull(matriz, "matriz");
    }

    public List<LinhaMatrizSaldos> executar(Sessao usuarioAutenticado, TenantId enteId, int exercicio, int mes) {
        controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_MATRIZ_SALDOS, Acao.LER);
        return matriz.matriz(enteId, exercicio, mes);
    }
}
