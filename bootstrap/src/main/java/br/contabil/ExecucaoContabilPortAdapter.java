package br.contabil;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.contabil.execucao.domain.repository.ExecucaoContabilPort;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;

/**
 * Ponte {@code execucao} -> {@code razao}: única classe do monólito que conhece
 * os dois módulos (execucao-orcamentaria-despesa.md §Fronteiras — "execução
 * conhece o razão"), por isso vive no {@code bootstrap} (composition root), não
 * em {@code execucao-infra} nem em {@code razao-infra} — nenhum dos dois
 * módulos de negócio depende do outro (guardiao-arquitetura, ArchUnit
 * {@code modulos_de_negocio_sao_livres_de_ciclo}).
 *
 * <p>Usa os ports de baixo nível do razão ({@link PeriodoContabilPort},
 * {@link ContadorFatoPort}, {@link FatoContabilRepository} +
 * {@link FatoContabil#registrar}) em vez do use case {@code RegistrarFatoContabil}
 * porque este último já teria seu próprio {@code ControleAcesso.exigir(...)} no
 * recurso {@code razao:fato_contabil} — redundante e com escopo de RBAC
 * diferente do gate único que o use case de execução (ex.: RegistrarEmpenho) já
 * fez no recurso {@code execucao:empenho}. A escrituração aqui roda dentro da
 * MESMA transação aberta pelo advisor da execução (ADR-0021 §atomicidade).
 *
 * <p>Roteiro de contabilização (ADR-0021) — códigos PCASP <b>representativos,
 * a revalidar na fonte oficial MCASP/PCASP</b> antes de fechar em produção.
 */
@Component
public class ExecucaoContabilPortAdapter implements ExecucaoContabilPort {

    private static final String CODIGO_CREDITO_DISPONIVEL = "6.2.2.1.1";
    private static final String CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR = "6.2.2.1.3";

    private static final String SQL_RESOLVER_CONTA = "select id from conta_pcasp where ente_id = ? and codigo = ?";

    private final JdbcTemplate jdbcTemplate;
    private final PeriodoContabilPort periodoContabil;
    private final ContadorFatoPort contadorFato;
    private final FatoContabilRepository fatoContabilRepositorio;
    private final Clock clock;

    public ExecucaoContabilPortAdapter(
            JdbcTemplate jdbcTemplate,
            PeriodoContabilPort periodoContabil,
            ContadorFatoPort contadorFato,
            FatoContabilRepository fatoContabilRepositorio,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.periodoContabil = periodoContabil;
        this.contadorFato = contadorFato;
        this.fatoContabilRepositorio = fatoContabilRepositorio;
        this.clock = clock;
    }

    @Override
    public UUID registrarEmpenho(SolicitacaoEscrituracaoEmpenho solicitacao) {
        ContaContabilId contaCreditoDisponivel = resolverConta(solicitacao.enteId(), CODIGO_CREDITO_DISPONIVEL);
        ContaContabilId contaEmpenhadoALiquidar = resolverConta(solicitacao.enteId(), CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR);

        List<Lancamento> lancamentos = List.of(
                Lancamento.de(contaEmpenhadoALiquidar, Natureza.DEBITO, solicitacao.valor()),
                Lancamento.de(contaCreditoDisponivel, Natureza.CREDITO, solicitacao.valor()));

        var periodoId = periodoContabil.periodoAbertoPara(solicitacao.enteId(), solicitacao.dataFato());
        long numeroSeq = contadorFato.proximoNumeroSeq(solicitacao.enteId());

        FatoContabil fato = FatoContabil.registrar(
                solicitacao.enteId(),
                numeroSeq,
                solicitacao.dataFato(),
                periodoId,
                TipoEvento.EMPENHO,
                solicitacao.historico(),
                "execucao:empenho:%s".formatted(solicitacao.empenhoId().valor()),
                lancamentos,
                clock);

        fatoContabilRepositorio.inserir(fato);
        return fato.id().valor();
    }

    @Override
    public UUID registrarLiquidacao(SolicitacaoEscrituracaoLiquidacao solicitacao) {
        throw new UnsupportedOperationException("RAZ-67: roteiro de contabilização da liquidação ainda não implementado");
    }

    @Override
    public UUID registrarPagamento(SolicitacaoEscrituracaoPagamento solicitacao) {
        throw new UnsupportedOperationException("RAZ-67: roteiro de contabilização do pagamento ainda não implementado");
    }

    private ContaContabilId resolverConta(TenantId enteId, String codigoPcasp) {
        List<UUID> ids = jdbcTemplate.query(
                SQL_RESOLVER_CONTA, (rs, rowNum) -> rs.getObject("id", UUID.class), enteId.valor(), codigoPcasp);
        if (ids.isEmpty()) {
            throw new ContaPcaspNaoEncontradaException(enteId, codigoPcasp);
        }
        return new ContaContabilId(ids.get(0));
    }
}
