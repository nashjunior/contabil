package br.contabil.plataforma.infra.entrega;

import java.time.Instant;
import java.util.List;

import br.contabil.plataforma.domain.entrega.ServicoEntrega.IdEntrega;

interface OutboxMensagemRepository {

    List<MensagemOutbox> reclamar(int limite, Instant bloqueadoAte);

    void confirmarEntrega(IdEntrega id);

    void registrarRetentativa(IdEntrega id, Instant proximaTentativa, String erro);

    void enviarParaDlq(IdEntrega id, String erro);
}
