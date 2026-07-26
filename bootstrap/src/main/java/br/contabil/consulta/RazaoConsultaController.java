package br.contabil.consulta;

import java.util.Objects;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.application.ConsultarSaldo;
import br.contabil.razao.application.GerarBalancete;
import br.contabil.razao.domain.ContaContabilId;

/**
 * Borda HTTP fina dos use cases de consulta do razão (RAZ-101): só adapta
 * HTTP → {@code executar(Sessao, TenantId, ...)}, sem lógica de negócio
 * (guardiao-arquitetura — controller é {@code infra}/borda). A {@link Sessao}
 * chega já autenticada via {@link SessaoAutenticadaHttpResolver}; erros de
 * negócio ({@code ErroContrato}) são mapeados por
 * {@link ErroContratoExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/entes/{enteId}/razao")
final class RazaoConsultaController {

    private final ConsultarSaldo consultarSaldo;
    private final GerarBalancete gerarBalancete;

    RazaoConsultaController(ConsultarSaldo consultarSaldo, GerarBalancete gerarBalancete) {
        this.consultarSaldo = Objects.requireNonNull(consultarSaldo, "consultarSaldo");
        this.gerarBalancete = Objects.requireNonNull(gerarBalancete, "gerarBalancete");
    }

    @GetMapping("/saldo")
    SaldoContaResponse saldo(
            @PathVariable("enteId") UUID enteId, @RequestParam("contaId") UUID contaId, Sessao sessao) {
        var saldo = consultarSaldo.executar(sessao, new TenantId(enteId), new ContaContabilId(contaId));
        return new SaldoContaResponse(contaId, saldo);
    }

    @GetMapping("/balancete")
    BalanceteResponse balancete(
            @PathVariable("enteId") UUID enteId,
            @RequestParam("exercicio") int exercicio,
            @RequestParam("mes") int mes,
            Sessao sessao) {
        var balancete = gerarBalancete.executar(sessao, new TenantId(enteId), exercicio, mes);
        return BalanceteResponse.de(balancete);
    }
}
