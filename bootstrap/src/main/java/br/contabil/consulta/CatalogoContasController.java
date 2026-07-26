package br.contabil.consulta;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.application.ConsultarContas;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.ContaResumo;
import br.contabil.razao.domain.PaginaContas;

/**
 * Borda HTTP fina do catálogo/busca de contas do PCASP (RAZ-117 / ADR-0030 §6):
 * {@code GET /api/v1/entes/{enteId}/razao/contas?busca=&cursor=&limit=} — lista do §6.1
 * ({@code {itens, proximoCursor}}, cursor opaco). Controller próprio (não em
 * {@link RazaoConsultaController}) porque o catálogo tem read port e escopo próprios
 * (ADR-0030 §6, "escopo próprio"); só adapta HTTP → {@code executar(Sessao, TenantId, ...)},
 * sem lógica de negócio (guardiao-arquitetura — controller é borda). A {@link Sessao} chega
 * já autenticada via {@link SessaoAutenticadaHttpResolver}; o clamp de {@code limit} e a busca
 * vivem no use case, nunca aqui.
 */
@RestController
@RequestMapping("/api/v1/entes/{enteId}/razao")
final class CatalogoContasController {

    private final ConsultarContas consultarContas;

    CatalogoContasController(ConsultarContas consultarContas) {
        this.consultarContas = Objects.requireNonNull(consultarContas, "consultarContas");
    }

    @GetMapping("/contas")
    ContasResponse contas(
            @PathVariable("enteId") UUID enteId,
            @RequestParam(value = "busca", required = false) String busca,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            Sessao sessao) {
        PaginaContas pagina = consultarContas.executar(
                sessao,
                new TenantId(enteId),
                Optional.ofNullable(busca),
                Optional.ofNullable(limit),
                Optional.ofNullable(cursor));
        return ContasResponse.de(pagina);
    }

    record ContasResponse(List<ContaResumoResponse> itens, String proximoCursor) {

        static ContasResponse de(PaginaContas pagina) {
            return new ContasResponse(
                    pagina.itens().stream().map(ContaResumoResponse::de).toList(),
                    pagina.proximoCursor().orElse(null));
        }
    }

    record ContaResumoResponse(
            UUID id,
            String codigo,
            String descricao,
            String naturezaSaldo,
            String naturezaInformacao,
            boolean escrituravel,
            UUID contaPaiId) {

        static ContaResumoResponse de(ContaResumo conta) {
            return new ContaResumoResponse(
                    conta.id().valor(),
                    conta.codigo(),
                    conta.descricao(),
                    conta.naturezaSaldo(),
                    conta.naturezaInformacao(),
                    conta.escrituravel(),
                    Optional.ofNullable(conta.contaPaiId()).map(ContaContabilId::valor).orElse(null));
        }
    }
}
