package br.contabil.execucao.domain.repository;

import java.time.LocalDate;
import java.util.Optional;

import br.contabil.execucao.domain.PaginaEmpenhosRegistrados;
import br.contabil.plataforma.domain.TenantId;

/**
 * Read model do registro de empenhos por ente/período (RAZ-121) — a listagem que
 * faltava para uma tela de "execuções registradas" persistida de verdade (em vez
 * de cache de sessão do cliente). Port próprio (não reaproveita {@link
 * EmpenhoRepository}, que é o repositório de escrita/leitura pontual por id):
 * mesma separação de {@link FilaAprovacaoQuery} vs {@code LiquidacaoRepository}.
 */
public interface EmpenhosRegistradosQuery {

    PaginaEmpenhosRegistrados consultar(
            TenantId enteId,
            Optional<Integer> exercicio,
            Optional<LocalDate> dataInicio,
            Optional<LocalDate> dataFim,
            int limite,
            Optional<String> cursor);
}
