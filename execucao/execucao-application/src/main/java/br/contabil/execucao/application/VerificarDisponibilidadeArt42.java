package br.contabil.execucao.application;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import br.contabil.execucao.domain.DisponibilidadeArt42InsuficienteException;
import br.contabil.execucao.domain.JanelaMandato;
import br.contabil.execucao.domain.repository.DisponibilidadeArt42Port;
import br.contabil.execucao.domain.repository.JanelaMandatoPort;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/**
 * Pré-condição do gate transacional do art. 42 da LRF (ADR-0044, reusa o padrão do
 * ADR-0023) — recusa contrair obrigação cuja fonte não lastreie, mas SÓ dentro dos
 * dois últimos quadrimestres do mandato ({@link JanelaMandato}); fora dela o cálculo é
 * monitor, não bloqueia.
 *
 * <p>POJO reusável entre {@code RegistrarEmpenho} (contrair obrigação) e, mais adiante,
 * {@code InscreverRestosAPagar} no encerramento (mesma trava, mesmo texto legal) — por
 * isso não é um caso de uso próprio com RBAC/auditoria (o chamador já fez o seu gate),
 * só a verificação em si. Método não se chama {@code executar(..)} de propósito: não é
 * um entrypoint de use case (o advisor de tenant/RLS já está ativo na chamada externa
 * que o invoca).
 */
public class VerificarDisponibilidadeArt42 {

    private final JanelaMandatoPort janelaMandato;
    private final DisponibilidadeArt42Port disponibilidade;

    public VerificarDisponibilidadeArt42(JanelaMandatoPort janelaMandato, DisponibilidadeArt42Port disponibilidade) {
        this.janelaMandato = Objects.requireNonNull(janelaMandato, "janela de mandato");
        this.disponibilidade = Objects.requireNonNull(disponibilidade, "disponibilidade art. 42");
    }

    /**
     * @param fonteRecurso nullable — ente sem vinculação de fonte não tem apuração por
     *     fonte, logo não há o que travar (mesmo racional condicional do roteiro DDR).
     *     {@code Empenho} hoje exige {@code fonteRecurso} não-nulo, então este ramo é
     *     inalcançável a partir de {@code RegistrarEmpenho}; a nulidade é tolerada aqui
     *     para o reuso futuro em {@code InscreverRestosAPagar} (RAZ-207), cujo modelo
     *     pode não ter essa mesma obrigatoriedade.
     * @throws DisponibilidadeArt42InsuficienteException quando, dentro da janela, a
     *     disponibilidade líquida da fonte é menor que {@code valor}.
     */
    public void verificar(TenantId enteId, LocalDate dataFato, String fonteRecurso, Dinheiro valor) {
        if (fonteRecurso == null) {
            return;
        }
        Optional<JanelaMandato> mandato = janelaMandato.buscar(enteId);
        if (mandato.isEmpty() || !mandato.get().estaNosUltimosDoisQuadrimestres(dataFato)) {
            return;
        }
        Optional<Dinheiro> saldoDisponivel = disponibilidade.saldoDisponivel(enteId, fonteRecurso);
        if (saldoDisponivel.isEmpty()) {
            return;
        }
        if (saldoDisponivel.get().compareTo(valor) < 0) {
            throw new DisponibilidadeArt42InsuficienteException(fonteRecurso, valor, saldoDisponivel.get());
        }
    }
}
