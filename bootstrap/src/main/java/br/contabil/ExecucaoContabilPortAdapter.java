package br.contabil;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.repository.DisponibilidadeArt42Port;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.ExecucaoOrcamentaria;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.FonteRecurso;
import br.contabil.razao.domain.InformacaoComplementar;
import br.contabil.razao.domain.InformacoesComplementares;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.DisponibilidadePorFontePort;
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
 *
 * <p>Também implementa {@link DisponibilidadeArt42Port} (RAZ-243/ADR-0044): mesma
 * ponte execução↔razão, agora em leitura — resolve a conta DDR "disponível a
 * comprometer" e delega a apuração por fonte a {@link DisponibilidadePorFontePort}
 * (razão), sem {@code execucao} depender de tipos do razão.
 *
 * <p><b>Informações complementares (RAZ-268/ADR-0057):</b> a {@code FonteRecurso}
 * (FR) e a {@code ExecucaoOrcamentaria} (CO) são capturadas AQUI, na origem — FR
 * vem da solicitação (empenho) ou é resolvida via {@code empenho} para
 * liquidação/pagamento; CO idem, sempre a partir do {@code empenho} de origem —
 * e anexadas às partidas orçamentárias/de controle (DDR) de cada fato, nunca às
 * patrimoniais/financeiras. A regra "conta X exige IC Y" (condicional por conta,
 * ADR-0050) é validada em {@link #exigirIcObrigatoria} antes de cada lançamento
 * ser montado — um lançamento sem a IC exigida pela sua conta é erro de
 * escrituração ({@link InformacaoComplementarObrigatoriaException}), nunca um
 * valor inferido depois. O mapa conta→IC é {@code [REVALIDAR]} contra o MCASP
 * edição vigente, assim como os códigos PCASP abaixo.
 */
@Component
public class ExecucaoContabilPortAdapter implements ExecucaoContabilPort, DisponibilidadeArt42Port {

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

    /**
     * Mapa conta PCASP → IC que ela exige (ADR-0050 "conta X exige IC Y"), aplicado por
     * {@link #exigirIcObrigatoria}. {@code [REVALIDAR]} contra o MCASP edição vigente — as
     * contas orçamentárias exigem {@code CO} (classificação orçamentária do empenho de
     * origem); as de controle DDR exigem também {@code FR} (já sustentava a trava do art.
     * 42). Contas patrimoniais/financeiras (VPD, Fornecedores, Caixa) não entram aqui —
     * essas dimensões não se aplicam a elas (ADR-0050).
     */
    private static final Map<String, Set<InformacaoComplementar>> IC_OBRIGATORIA_POR_CONTA = Map.of(
            CODIGO_CREDITO_DISPONIVEL, EnumSet.of(InformacaoComplementar.CO),
            CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR, EnumSet.of(InformacaoComplementar.CO),
            CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR, EnumSet.of(InformacaoComplementar.CO),
            CODIGO_EMPENHADO_PAGO, EnumSet.of(InformacaoComplementar.CO),
            CODIGO_DDR_DISPONIVEL, EnumSet.of(InformacaoComplementar.FR, InformacaoComplementar.CO),
            CODIGO_DDR_COMPROMETIDA_EMPENHO, EnumSet.of(InformacaoComplementar.FR, InformacaoComplementar.CO),
            CODIGO_DDR_EM_LIQUIDACAO, EnumSet.of(InformacaoComplementar.FR, InformacaoComplementar.CO),
            CODIGO_DDR_UTILIZADA, EnumSet.of(InformacaoComplementar.FR, InformacaoComplementar.CO));

    private static final String SQL_RESOLVER_CONTA = "select id from conta_pcasp where ente_id = ? and codigo = ?";
    private static final String SQL_CLASSIFICACOES_EMPENHO =
            "select classificacao_orcamentaria, fonte_recurso from empenho where ente_id = ? and id = ?";
    private static final String SQL_CLASSIFICACOES_VIA_LIQUIDACAO =
            "select e.classificacao_orcamentaria, e.fonte_recurso from empenho e"
                    + " join liquidacao l on l.ente_id = e.ente_id and l.empenho_id = e.id"
                    + " where l.ente_id = ? and l.id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final PeriodoContabilPort periodoContabil;
    private final ContadorFatoPort contadorFato;
    private final FatoContabilRepository fatoContabilRepositorio;
    private final DisponibilidadePorFontePort disponibilidadePorFonte;
    private final Clock clock;

    public ExecucaoContabilPortAdapter(
            JdbcTemplate jdbcTemplate,
            PeriodoContabilPort periodoContabil,
            ContadorFatoPort contadorFato,
            FatoContabilRepository fatoContabilRepositorio,
            DisponibilidadePorFontePort disponibilidadePorFonte,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.periodoContabil = periodoContabil;
        this.contadorFato = contadorFato;
        this.fatoContabilRepositorio = fatoContabilRepositorio;
        this.disponibilidadePorFonte = disponibilidadePorFonte;
        this.clock = clock;
    }

    @Override
    public ReferenciaFatoContabil registrarEmpenho(SolicitacaoEscrituracaoEmpenho solicitacao) {
        ContaContabilId contaCreditoDisponivel = resolverConta(solicitacao.enteId(), CODIGO_CREDITO_DISPONIVEL);
        ContaContabilId contaEmpenhadoALiquidar = resolverConta(solicitacao.enteId(), CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR);

        FonteRecurso fr = solicitacao.fonteRecurso() == null ? null : FonteRecurso.de(solicitacao.fonteRecurso());
        ExecucaoOrcamentaria co = ExecucaoOrcamentaria.de(solicitacao.classificacaoOrcamentaria());
        InformacoesComplementares icOrcamentaria = InformacoesComplementares.de(null, co, null, null, null, null);

        List<Lancamento> lancamentos = new ArrayList<>(List.of(
                lancamento(
                        contaEmpenhadoALiquidar, CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR, Natureza.DEBITO,
                        solicitacao.valor(), icOrcamentaria),
                lancamento(
                        contaCreditoDisponivel, CODIGO_CREDITO_DISPONIVEL, Natureza.CREDITO, solicitacao.valor(),
                        icOrcamentaria)));

        // DDR (ADR-0054): só quando FR presente e contas de controle classes 7/8 provisionadas
        if (fr != null) {
            InformacoesComplementares icDdr = InformacoesComplementares.de(fr, co, null, null, null, null);
            resolverContaOpcional(solicitacao.enteId(), CODIGO_DDR_DISPONIVEL).ifPresent(ddrDisponivel ->
                resolverContaOpcional(solicitacao.enteId(), CODIGO_DDR_COMPROMETIDA_EMPENHO)
                    .ifPresent(ddrComprometida -> {
                        lancamentos.add(lancamento(
                                ddrComprometida, CODIGO_DDR_COMPROMETIDA_EMPENHO, Natureza.DEBITO, solicitacao.valor(),
                                icDdr));
                        lancamentos.add(lancamento(
                                ddrDisponivel, CODIGO_DDR_DISPONIVEL, Natureza.CREDITO, solicitacao.valor(), icDdr));
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

        ClassificacoesEmpenho classificacoes =
                classificacoesDoEmpenho(solicitacao.enteId(), solicitacao.empenhoId().valor());
        ExecucaoOrcamentaria co = ExecucaoOrcamentaria.de(classificacoes.classificacaoOrcamentaria());
        InformacoesComplementares icOrcamentaria = InformacoesComplementares.de(null, co, null, null, null, null);

        List<Lancamento> lancamentos = new ArrayList<>(List.of(
                lancamento(contaVpd, CODIGO_VPD, Natureza.DEBITO, solicitacao.valor(), InformacoesComplementares.nenhuma()),
                lancamento(
                        contaFornecedoresAPagar, CODIGO_FORNECEDORES_A_PAGAR, Natureza.CREDITO, solicitacao.valor(),
                        InformacoesComplementares.nenhuma()),
                lancamento(
                        contaEmpenhadoLiquidadoAPagar, CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR, Natureza.DEBITO,
                        solicitacao.valor(), icOrcamentaria),
                lancamento(
                        contaEmpenhadoALiquidar, CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR, Natureza.CREDITO,
                        solicitacao.valor(), icOrcamentaria)));

        // DDR: baixa a comprometida por empenho, registra em liquidação
        classificacoes.fonteRecurso().ifPresent(fr -> {
            InformacoesComplementares icDdr = InformacoesComplementares.de(fr, co, null, null, null, null);
            resolverContaOpcional(solicitacao.enteId(), CODIGO_DDR_COMPROMETIDA_EMPENHO).ifPresent(ddrComprometida ->
                resolverContaOpcional(solicitacao.enteId(), CODIGO_DDR_EM_LIQUIDACAO).ifPresent(ddrEmLiquidacao -> {
                    lancamentos.add(lancamento(
                            ddrEmLiquidacao, CODIGO_DDR_EM_LIQUIDACAO, Natureza.DEBITO, solicitacao.valor(), icDdr));
                    lancamentos.add(lancamento(
                            ddrComprometida, CODIGO_DDR_COMPROMETIDA_EMPENHO, Natureza.CREDITO, solicitacao.valor(),
                            icDdr));
                }));
        });

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

        ClassificacoesEmpenho classificacoes =
                classificacoesViaLiquidacao(solicitacao.enteId(), solicitacao.liquidacaoId().valor());
        ExecucaoOrcamentaria co = ExecucaoOrcamentaria.de(classificacoes.classificacaoOrcamentaria());
        InformacoesComplementares icOrcamentaria = InformacoesComplementares.de(null, co, null, null, null, null);

        List<Lancamento> lancamentos = new ArrayList<>(List.of(
                lancamento(
                        contaFornecedoresAPagar, CODIGO_FORNECEDORES_A_PAGAR, Natureza.DEBITO, solicitacao.valor(),
                        InformacoesComplementares.nenhuma()),
                lancamento(
                        contaCaixaEBancos, CODIGO_CAIXA_E_BANCOS, Natureza.CREDITO, solicitacao.valor(),
                        InformacoesComplementares.nenhuma()),
                lancamento(
                        contaEmpenhadoPago, CODIGO_EMPENHADO_PAGO, Natureza.DEBITO, solicitacao.valor(),
                        icOrcamentaria),
                lancamento(
                        contaEmpenhadoLiquidadoAPagar, CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR, Natureza.CREDITO,
                        solicitacao.valor(), icOrcamentaria)));

        // DDR: baixa "em liquidação", registra como utilizada/paga
        classificacoes.fonteRecurso().ifPresent(fr -> {
            InformacoesComplementares icDdr = InformacoesComplementares.de(fr, co, null, null, null, null);
            resolverContaOpcional(solicitacao.enteId(), CODIGO_DDR_EM_LIQUIDACAO).ifPresent(ddrEmLiquidacao ->
                resolverContaOpcional(solicitacao.enteId(), CODIGO_DDR_UTILIZADA).ifPresent(ddrUtilizada -> {
                    lancamentos.add(lancamento(
                            ddrUtilizada, CODIGO_DDR_UTILIZADA, Natureza.DEBITO, solicitacao.valor(), icDdr));
                    lancamentos.add(lancamento(
                            ddrEmLiquidacao, CODIGO_DDR_EM_LIQUIDACAO, Natureza.CREDITO, solicitacao.valor(), icDdr));
                }));
        });

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

    /**
     * RAZ-243/ADR-0044: disponibilidade líquida da fonte na conta DDR "disponível a
     * comprometer" (mesma conta creditada em {@link #registrarEmpenho}). Vazio quando a
     * conta DDR não está provisionada (sem como apurar — não bloqueia); presente com
     * {@link Dinheiro#zero()} quando a conta existe mas a fonte não tem saldo apurado
     * (zero é uma resposta válida, distinta de "sem dado").
     */
    @Override
    public Optional<Dinheiro> saldoDisponivel(TenantId enteId, String fonteRecurso) {
        Optional<ContaContabilId> contaDisponivel = resolverContaOpcional(enteId, CODIGO_DDR_DISPONIVEL);
        if (contaDisponivel.isEmpty()) {
            return Optional.empty();
        }
        FonteRecurso fonte = FonteRecurso.de(fonteRecurso);
        return Optional.of(disponibilidadePorFonte
                .consultarSaldoPorFonte(enteId, List.of(contaDisponivel.get()))
                .stream()
                .filter(saldo -> saldo.fonte().equals(fonte))
                .map(DisponibilidadePorFontePort.SaldoPorFonte::saldoDevedorLiquido)
                .findFirst()
                .orElse(Dinheiro.zero()));
    }

    /**
     * Monta o lançamento já validando a regra "conta X exige IC Y" (ADR-0050) — nunca
     * deixa uma partida sem a IC que sua conta exige chegar ao motor do razão.
     */
    private static Lancamento lancamento(
            ContaContabilId contaId, String codigoPcasp, Natureza natureza, Dinheiro valor,
            InformacoesComplementares ic) {
        exigirIcObrigatoria(codigoPcasp, ic);
        return Lancamento.de(contaId, natureza, valor, ic);
    }

    private static void exigirIcObrigatoria(String codigoPcasp, InformacoesComplementares ic) {
        Set<InformacaoComplementar> exigidas = IC_OBRIGATORIA_POR_CONTA.getOrDefault(codigoPcasp, Set.of());
        Set<InformacaoComplementar> presentes = ic.presentes();
        for (InformacaoComplementar exigida : exigidas) {
            if (!presentes.contains(exigida)) {
                throw new InformacaoComplementarObrigatoriaException(codigoPcasp, exigida);
            }
        }
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

    /** A {@code classificacaoOrcamentaria} (CO, coluna obrigatória) e a {@code fonteRecurso} (FR, nullable) do empenho. */
    private record ClassificacoesEmpenho(String classificacaoOrcamentaria, Optional<FonteRecurso> fonteRecurso) {}

    private ClassificacoesEmpenho classificacoesDoEmpenho(TenantId enteId, UUID empenhoId) {
        return classificacoesEmpenho(SQL_CLASSIFICACOES_EMPENHO, enteId, empenhoId);
    }

    private ClassificacoesEmpenho classificacoesViaLiquidacao(TenantId enteId, UUID liquidacaoId) {
        return classificacoesEmpenho(SQL_CLASSIFICACOES_VIA_LIQUIDACAO, enteId, liquidacaoId);
    }

    private ClassificacoesEmpenho classificacoesEmpenho(String sql, TenantId enteId, UUID id) {
        List<ClassificacoesEmpenho> linhas = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ClassificacoesEmpenho(
                        rs.getString("classificacao_orcamentaria"),
                        Optional.ofNullable(rs.getString("fonte_recurso")).map(FonteRecurso::de)),
                enteId.valor(), id);
        if (linhas.isEmpty()) {
            throw new IllegalStateException(
                    "empenho de origem não encontrado para escriturar CO/FR (ente %s, id %s)".formatted(enteId, id));
        }
        return linhas.get(0);
    }
}
