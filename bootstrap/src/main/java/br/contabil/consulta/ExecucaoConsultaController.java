package br.contabil.consulta;

import java.util.Objects;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.contabil.execucao.application.ConsultarExecucaoOrcamentaria;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Borda HTTP fina do use case de consulta da execução orçamentária (RAZ-101):
 * só adapta HTTP → {@code executar(Sessao, TenantId, ...)}, sem lógica de
 * negócio (guardiao-arquitetura — controller é {@code infra}/borda).
 */
@RestController
@RequestMapping("/api/v1/entes/{enteId}/execucao")
final class ExecucaoConsultaController {

    private final ConsultarExecucaoOrcamentaria consultarExecucaoOrcamentaria;

    ExecucaoConsultaController(ConsultarExecucaoOrcamentaria consultarExecucaoOrcamentaria) {
        this.consultarExecucaoOrcamentaria =
                Objects.requireNonNull(consultarExecucaoOrcamentaria, "consultarExecucaoOrcamentaria");
    }

    @GetMapping("/orcamentaria")
    ExecucaoOrcamentariaResponse orcamentaria(
            @PathVariable("enteId") UUID enteId,
            @RequestParam("exercicio") int exercicio,
            @RequestParam("mes") int mes,
            Sessao sessao) {
        var execucao = consultarExecucaoOrcamentaria.executar(sessao, new TenantId(enteId), exercicio, mes);
        return ExecucaoOrcamentariaResponse.de(execucao);
    }
}
