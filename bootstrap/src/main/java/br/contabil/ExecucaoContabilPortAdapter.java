package br.contabil;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.FonteRecurso;
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
    private static final String CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR = "6.2.2.1.4";
    private static final String CODIGO_EMPENHADO_PAGO = "6.2.2.1.5";
    private static final String CODIGO_VPD = "3.3.3.1.01";
    private static final String CODIGO_FORNECEDORES_A_PAGAR = "2.1.3";
    private static final String CODIGO_CAIXA_E_BANCOS = "1.1.1";

    // DDR — classes 7/8 PCASP (Controle/Execução da Disponibilidade por Destinação de Recursos, ADR-0054).
    // [REVALIDAR] contra MCASP edição vigente antes de produção.
    private static final String CODIGO_DDR_DISPONIVEL = "8.2.1.1.1";         // DDR a comprometer
    private static final String CODIGO_DDR_COMPROMETIDA_EMPENHO = "8.2.1.1.2"; // DDR comprometida por empenho
    private static final String CODIGO_DDR_EM_LIQUIDACAO = "8.2.2.1.2";       // DDR em liquidação
    private static final String CODIGO_DDR_UTILIZADA = "8.2.3.1.1";           // DDR utilizada (paga)

    private static final String SQL_RESOLVER_CONTA = "select id from conta_pcasp where ente_id = ? and codigo = ?";
    private static final String SQL_FONTE_RECURSO_EMPENHO =
            "select fonte_recurso from empenho where ente_id = ? and id = ?";
    private static final String SQL_FONTE_RECURSO_VIA_LIQUIDACAO =
            "select e.fonte_recurso from empenho e"
                    + " join liquidacao l on l.ente_id = e.ente_id and l.empenho_id = e.id"
                    + " where l.ente_id = ? and l.id = ?";

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
    public ReferenciaFatoContabil registrarEmpenho(SolicitacaoEscrituracaoEmpenho solicitacao) {
        ContaContabilId contaCreditoDisponivel = resolverConta(solicitacao.enteId(), CODIGO_CREDITO_DISPONIVEL);
        ContaContabilId contaEmpenhadoALiquidar = resolverConta(solicitacao.enteId(), CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR);

        List<Lancamento> lancamentos = new ArrayList<>(List.of(
                Lancamento.de(contaEmpenhadoALiquidar, Natureza.DEBITO, solicitacao.valor()),
                Lancamento.de(contaCreditoDisponivel, Natureza.CREDITO, solicitacao.valor())));

        // DDR (ADR-0054): só quando FR presente e contas de controle classes 7/8 provisionadas
        if (solicitacao.fonteRecurso() != null) {
            FonteRecurso fr = FonteRecurso.de(solicitacao.fonteRecurso());
            resolverContaOpcional(solicitacao.enteId(), CODIGO_DDR_DISPONIVEL).ifPresent(ddrDisponivel ->
                resolverContaOpcional(solicitacao.enteId(), CODIGO_DDR_COMPROMETIDA_EMPENHO)
                    .ifPresent(ddrComprometida -> {
                        lancamentos.add(Lancamento.de(ddrComprometida, Natureza.DEBITO, solicitacao.valor(), fr));
                        lancamentos.add(Lancamento.de(ddrDisponivel, Natureza.CREDITO, solicitacao.valor(), fr));
                    }));
        }

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
        return new ReferenciaFatoContabil(fato.id().valor());
    }

    /**
     * RAZ-67/RAZ-105: liquidação toca dois subsistemas PCASP no MESMO fato (ADR-0021) — patrimonial
     * (reconhece a VPD e o passivo com o fornecedor, fato gerador por competência, Lei 4.320 art. 35)
     * e orçamentário (baixa o "empenhado a liquidar", abre o "empenhado liquidado a pagar"). Os
     * quatro lançamentos, juntos, já fecham Σdébito=Σcrédito — não é preciso balancear por
     * subsistema isoladamente.
     */
    @Override
    public ReferenciaFatoContabil registrarLiquidacao(SolicitacaoEscrituracaoLiquidacao solicitacao) {
        ContaContabilId contaVpd = resolverConta(solicitacao.enteId(), CODIGO_VPD);
        ContaContabilId contaFornecedoresAPagar = resolverConta(solicitacao.enteId(), CODIGO_FORNECEDORES_A_PAGAR);
        ContaContabilId contaEmpenhadoALiquidar = resolverConta(solicitacao.enteId(), CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR);
        ContaContabilId contaEmpenhadoLiquidadoAPagar =
                resolverConta(solicitacao.enteId(), CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR);

        List<Lancamento> lancamentos = new ArrayList<>(List.of(
                Lancamento.de(contaVpd, Natureza.DEBITO, solicitacao.valor()),
                Lancamento.de(contaFornecedoresAPagar, Natureza.CREDITO, solicitacao.valor()),
                Lancamento.de(contaEmpenhadoLiquidadoAPagar, Natureza.DEBITO, solicitacao.valor()),
                Lancamento.de(contaEmpenhadoALiquidar, Natureza.CREDITO, solicitacao.valor())));

        // DDR: baixa a comprometida por empenho, registra em liquidação
        fonteRecursoDoEmpenho(solicitacao.enteId(), solicitacao.empenhoId().valor()).ifPresent(fr ->
            resolverContaOpcional(solicitacao.enteId(), CODIGO_DDR_COMPROMETIDA_EMPENHO).ifPresent(ddrComprometida ->
                resolverContaOpcional(solicitacao.enteId(), CODIGO_DDR_EM_LIQUIDACAO).ifPresent(ddrEmLiquidacao -> {
                    lancamentos.add(Lancamento.de(ddrEmLiquidacao, Natureza.DEBITO, solicitacao.valor(), fr));
                    lancamentos.add(Lancamento.de(ddrComprometida, Natureza.CREDITO, solicitacao.valor(), fr));
                })));

        var periodoId = periodoContabil.periodoAbertoPara(solicitacao.enteId(), solicitacao.dataCompetencia());
        long numeroSeq = contadorFato.proximoNumeroSeq(solicitacao.enteId());

        FatoContabil fato = FatoContabil.registrar(
                solicitacao.enteId(),
                numeroSeq,
                solicitacao.dataCompetencia(),
                periodoId,
                TipoEvento.LIQUIDACAO,
                solicitacao.historico(),
                "execucao:liquidacao:%s".formatted(solicitacao.liquidacaoId().valor()),
                lancamentos,
                clock);

        fatoContabilRepositorio.inserir(fato);
        return new ReferenciaFatoContabil(fato.id().valor());
    }

    /**
     * RAZ-67/RAZ-105: pagamento é a baixa financeira (ADR-0021) — patrimonial/financeiro (quita o
     * passivo com o fornecedor contra caixa/bancos) e orçamentário (fecha o "empenhado liquidado a
     * pagar", abre o "empenhado pago"). Mesma regra da liquidação: um fato só, Σ=Σ pelo total.
     */
    @Override
    public ReferenciaFatoContabil registrarPagamento(SolicitacaoEscrituracaoPagamento solicitacao) {
        ContaContabilId contaFornecedoresAPagar = resolverConta(solicitacao.enteId(), CODIGO_FORNECEDORES_A_PAGAR);
        ContaContabilId contaCaixaEBancos = resolverConta(solicitacao.enteId(), CODIGO_CAIXA_E_BANCOS);
        ContaContabilId contaEmpenhadoLiquidadoAPagar =
                resolverConta(solicitacao.enteId(), CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR);
        ContaContabilId contaEmpenhadoPago = resolverConta(solicitacao.enteId(), CODIGO_EMPENHADO_PAGO);

        List<Lancamento> lancamentos = new ArrayList<>(List.of(
                Lancamento.de(contaFornecedoresAPagar, Natureza.DEBITO, solicitacao.valor()),
                Lancamento.de(contaCaixaEBancos, Natureza.CREDITO, solicitacao.valor()),
                Lancamento.de(contaEmpenhadoPago, Natureza.DEBITO, solicitacao.valor()),
                Lancamento.de(contaEmpenhadoLiquidadoAPagar, Natureza.CREDITO, solicitacao.valor())));

        // DDR: baixa "em liquidação", registra como utilizada/paga
        fonteRecursoViaLiquidacao(solicitacao.enteId(), solicitacao.liquidacaoId().valor()).ifPresent(fr ->
            resolverContaOpcional(solicitacao.enteId(), CODIGO_DDR_EM_LIQUIDACAO).ifPresent(ddrEmLiquidacao ->
                resolverContaOpcional(solicitacao.enteId(), CODIGO_DDR_UTILIZADA).ifPresent(ddrUtilizada -> {
                    lancamentos.add(Lancamento.de(ddrUtilizada, Natureza.DEBITO, solicitacao.valor(), fr));
                    lancamentos.add(Lancamento.de(ddrEmLiquidacao, Natureza.CREDITO, solicitacao.valor(), fr));
                })));

        var periodoId = periodoContabil.periodoAbertoPara(solicitacao.enteId(), solicitacao.dataCompetencia());
        long numeroSeq = contadorFato.proximoNumeroSeq(solicitacao.enteId());

        FatoContabil fato = FatoContabil.registrar(
                solicitacao.enteId(),
                numeroSeq,
                solicitacao.dataCompetencia(),
                periodoId,
                TipoEvento.PAGAMENTO,
                solicitacao.historico(),
                "execucao:pagamento:%s".formatted(solicitacao.pagamentoId().valor()),
                lancamentos,
                clock);

        fatoContabilRepositorio.inserir(fato);
        return new ReferenciaFatoContabil(fato.id().valor());
    }

    private ContaContabilId resolverConta(TenantId enteId, String codigoPcasp) {
        List<UUID> ids = jdbcTemplate.query(
                SQL_RESOLVER_CONTA, (rs, rowNum) -> rs.getObject("id", UUID.class), enteId.valor(), codigoPcasp);
        if (ids.isEmpty()) {
            throw new ContaPcaspNaoEncontradaException(enteId, codigoPcasp);
        }
        return new ContaContabilId(ids.get(0));
    }

    // Retorna vazio se a conta DDR não estiver provisionada — ente sem vinculação pula DDR sem erro.
    private Optional<ContaContabilId> resolverContaOpcional(TenantId enteId, String codigoPcasp) {
        List<UUID> ids = jdbcTemplate.query(
                SQL_RESOLVER_CONTA, (rs, rowNum) -> rs.getObject("id", UUID.class), enteId.valor(), codigoPcasp);
        return ids.isEmpty() ? Optional.empty() : Optional.of(new ContaContabilId(ids.get(0)));
    }

    private Optional<FonteRecurso> fonteRecursoDoEmpenho(TenantId enteId, UUID empenhoId) {
        List<String> fontes = jdbcTemplate.query(
                SQL_FONTE_RECURSO_EMPENHO, (rs, rowNum) -> rs.getString("fonte_recurso"),
                enteId.valor(), empenhoId);
        return fontes.isEmpty() || fontes.get(0) == null
                ? Optional.empty()
                : Optional.of(FonteRecurso.de(fontes.get(0)));
    }

    private Optional<FonteRecurso> fonteRecursoViaLiquidacao(TenantId enteId, UUID liquidacaoId) {
        List<String> fontes = jdbcTemplate.query(
                SQL_FONTE_RECURSO_VIA_LIQUIDACAO, (rs, rowNum) -> rs.getString("fonte_recurso"),
                enteId.valor(), liquidacaoId);
        return fontes.isEmpty() || fontes.get(0) == null
                ? Optional.empty()
                : Optional.of(FonteRecurso.de(fontes.get(0)));
    }
}
