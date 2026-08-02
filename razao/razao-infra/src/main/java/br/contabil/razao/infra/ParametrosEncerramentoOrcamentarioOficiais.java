package br.contabil.razao.infra;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.application.ParametroEncerramentoOrcamentario;
import br.contabil.razao.application.ParametrosEncerramentoOrcamentario;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.Natureza;

/**
 * Parâmetros oficiais de encerramento das contas de controle orçamentário PCASP
 * (classes 5/6) centralizados na borda — RAZ-270 (mesmo padrão do RAZ-260/RAZ-266).
 *
 * <p>Cobre a execução da despesa (IPC 03/STN rev. 2017, item 46 — roteiro "sem controle
 * da dotação inicial e adicional no empenho", aplicável porque o SIAFIC não rastreia a
 * origem do crédito desde o empenho): os quatro controles de execução já usados na
 * escrituração da despesa (RAZ-30/ADR-0021, {@code ExecucaoContabilPortAdapter}) —
 * {@code 6.2.2.1.1} Crédito Disponível, {@code 6.2.2.1.3} Crédito Empenhado a Liquidar,
 * {@code 6.2.2.1.4} Empenhado Liquidado a Pagar, {@code 6.2.2.1.5} Empenhado Pago —
 * encerram individualmente contra {@code 5.2.2.1.1.01.00} Crédito Inicial (item 46,
 * alíneas b/d/e/g — a contrapartida é sempre creditada, o controle é sempre debitado).
 * Os saldos de {@code 6.2.2.1.3}/{@code 6.2.2.1.4} referentes a empenhos não pagos já
 * foram transferidos para as contas de RP antes desta etapa (o encerramento orçamentário
 * roda depois da inscrição de RP em {@code EncerrarExercicio}), então tipicamente
 * resolvem saldo zero e são ignorados pelo colaborador — o parâmetro cobre o caso geral.
 *
 * <p>A execução da receita (item 42 — {@code 6.2.1.1.0.00.00}/{@code 6.2.1.2.0.00.00}
 * contra {@code 5.2.1.1.1.00.00}) fica fora deste resolver: o SIAFIC ainda não tem caso
 * de uso de execução da receita (nenhum fato gera saldo nessas contas), então incluir os
 * pares aqui criaria uma exigência de cadastro sem função — revisitar quando a execução
 * da receita for implementada. A resolução é por {@code ente_id}, pois {@code conta_pcasp}
 * é tenant-scoped.
 */
final class ParametrosEncerramentoOrcamentarioOficiais implements ParametrosEncerramentoOrcamentario {

    private static final String CODIGO_CREDITO_DISPONIVEL = "6.2.2.1.1";
    private static final String CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR = "6.2.2.1.3";
    private static final String CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR = "6.2.2.1.4";
    private static final String CODIGO_EMPENHADO_PAGO = "6.2.2.1.5";
    private static final String CODIGO_CREDITO_INICIAL = "5.2.2.1.1.01.00";

    private static final String SQL_RESOLVER_CONTA = "select id from conta_pcasp where ente_id = ? and codigo = ?";

    private final JdbcTemplate jdbcTemplate;

    ParametrosEncerramentoOrcamentarioOficiais(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public List<ParametroEncerramentoOrcamentario> para(TenantId enteId) {
        Objects.requireNonNull(enteId, "enteId");

        ContaContabilId creditoInicial = resolverConta(enteId, CODIGO_CREDITO_INICIAL);

        return List.of(
                new ParametroEncerramentoOrcamentario(
                        resolverConta(enteId, CODIGO_CREDITO_DISPONIVEL), creditoInicial, Natureza.CREDITO),
                new ParametroEncerramentoOrcamentario(
                        resolverConta(enteId, CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR), creditoInicial, Natureza.CREDITO),
                new ParametroEncerramentoOrcamentario(
                        resolverConta(enteId, CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR), creditoInicial, Natureza.CREDITO),
                new ParametroEncerramentoOrcamentario(
                        resolverConta(enteId, CODIGO_EMPENHADO_PAGO), creditoInicial, Natureza.CREDITO));
    }

    private ContaContabilId resolverConta(TenantId enteId, String codigoPcasp) {
        List<UUID> ids = jdbcTemplate.query(
                SQL_RESOLVER_CONTA, (rs, rowNum) -> rs.getObject("id", UUID.class), enteId.valor(), codigoPcasp);
        if (ids.isEmpty()) {
            throw new IllegalStateException(
                    "Conta PCASP obrigatória para encerramento orçamentário não cadastrada: " + codigoPcasp);
        }
        return new ContaContabilId(ids.get(0));
    }
}
