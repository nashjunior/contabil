package br.contabil.execucao.domain;

import java.time.LocalDate;
import java.util.Objects;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Resumo leve de um empenho registrado (RAZ-121: registro de execução, distinto
 * da fila de aprovação — este é o "o que já foi lançado", não um gate). O credor
 * entra só como {@link CredorId} (referência opaca), mesma razão de {@link
 * ItemFilaAprovacao}: sem nome/CPF-CNPJ materializado na execução, então não há
 * PII em claro a mascarar aqui (04-lgpd).
 */
public record ItemEmpenhoRegistrado(
        EmpenhoId id,
        long numeroSequencial,
        int exercicio,
        TipoEmpenho tipo,
        CredorId credorId,
        Dinheiro valor,
        LocalDate dataFato,
        String historico,
        StatusEmpenho status) {

    public ItemEmpenhoRegistrado {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(credorId, "credorId");
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(dataFato, "dataFato");
        Objects.requireNonNull(historico, "historico");
        Objects.requireNonNull(status, "status");
    }
}
