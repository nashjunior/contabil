package br.contabil.escrita;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.contabil.execucao.application.RegistrarPagamento;
import br.contabil.execucao.domain.Beneficiario;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.NaturezaPagamento;
import br.contabil.execucao.domain.Pagamento;
import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.ErroContrato;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Borda HTTP do estágio de pagamento — individual e em lote (RAZ-105, RAZ-79
 * §6.5): só adapta HTTP → {@code RegistrarPagamento.executar(..)}, sem lógica
 * de negócio (guardiao-arquitetura — controller é infra/borda).
 *
 * <p>O lote (ADR-0013/ADR-0022) chama o MESMO caso de uso item a item — cada
 * item é sua própria transação atômica (via advisor de
 * {@code TransacaoUseCasesConfiguration}); o lote em si não abre transação
 * guarda-chuva. Um item ruim vira {@code errors[]}, nunca derruba os demais.
 *
 * <p>Idempotência (ADR-0011/RAZ-134): o endpoint individual aceita o header
 * {@code Idempotency-Key} (opcional); no lote, {@code chaveCliente} de cada
 * item — já obrigatório para correlacionar request↔response — também serve
 * como chave de idempotência server-side. Reenviar o mesmo lote/pagamento
 * após timeout/erro de rede devolve o resultado original em vez de duplicar
 * o lançamento contábil.
 */
@RestController
@RequestMapping("/api/v1/entes/{enteId}/execucao")
final class PagamentoController {

    private final RegistrarPagamento registrarPagamento;

    PagamentoController(RegistrarPagamento registrarPagamento) {
        this.registrarPagamento = Objects.requireNonNull(registrarPagamento, "registrarPagamento");
    }

    @PostMapping("/pagamentos")
    ResponseEntity<PagamentoResponse> registrar(
            @PathVariable("enteId") UUID enteId,
            @RequestBody PagamentoRequest requisicao,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Sessao sessao) {
        Pagamento pagamento = executar(enteId, requisicao, sessao, chaveIdempotencia("individual", idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(PagamentoResponse.de(pagamento));
    }

    @PostMapping("/pagamentos:lote")
    ResponseEntity<LotePagamentoResponse> registrarLote(
            @PathVariable("enteId") UUID enteId, @RequestBody LotePagamentoRequest requisicao, Sessao sessao) {
        List<ItemProcessado> processados = new ArrayList<>();
        List<ItemComErro> erros = new ArrayList<>();
        for (ItemLotePagamentoRequest item : requisicao.itens()) {
            try {
                Pagamento pagamento = executar(
                        enteId, item.paraPagamentoRequest(), sessao, chaveIdempotencia("lote", item.chaveCliente()));
                processados.add(new ItemProcessado(item.chaveCliente(), pagamento.id().valor(), pagamento.fatoContabilId().valor()));
            } catch (RuntimeException erro) {
                // Fail-soft (ADR-0013): item malformado (ex.: natureza/valor/UUID inválido, não só
                // ExecucaoInvalidaException) vira errors[], nunca aborta o restante do lote.
                String codigo = erro instanceof ErroContrato erroContrato ? erroContrato.codigo() : "item_invalido";
                erros.add(new ItemComErro(item.chaveCliente(), codigo, erro.getMessage()));
            }
        }
        return ResponseEntity.status(207).body(new LotePagamentoResponse(processados, erros));
    }

    private Pagamento executar(
            UUID enteId, PagamentoRequest requisicao, Sessao sessao, Optional<ChaveIdempotencia> chaveIdempotencia) {
        return registrarPagamento.executar(
                sessao,
                new TenantId(enteId),
                new LiquidacaoId(requisicao.liquidacaoId()),
                requisicao.dataCompetencia(),
                Dinheiro.de(requisicao.valor()),
                NaturezaPagamento.valueOf(requisicao.natureza().toUpperCase()),
                Optional.ofNullable(requisicao.beneficiario()).map(BeneficiarioRequest::paraDominio),
                Optional.ofNullable(requisicao.ordemBancaria()),
                requisicao.historico(),
                chaveIdempotencia);
    }

    /**
     * {@code chave} em branco/ausente = sem idempotência (comportamento anterior, sem opt-in).
     * {@code prefixo} evita colisão entre o {@code Idempotency-Key} do endpoint individual e o
     * {@code chaveCliente} do lote — sem ele, o mesmo valor usado nos dois pontos de entrada
     * (mesmo ente) faria curto-circuito para um pagamento não relacionado.
     */
    private static Optional<ChaveIdempotencia> chaveIdempotencia(String prefixo, String chave) {
        return (chave == null || chave.isBlank())
                ? Optional.empty()
                : Optional.of(ChaveIdempotencia.de(prefixo + ':' + chave));
    }

    record BeneficiarioRequest(String nome, String cpfCnpj) {

        Beneficiario paraDominio() {
            return new Beneficiario(nome, cpfCnpj);
        }
    }

    /**
     * {@code cpfCnpj} sempre mascarado na resposta (RAZ-79 §6.1: "toda resposta que inclui
     * beneficiario.cpfCnpj vem mascarada por padrão" — ver dados completos é uma capability à
     * parte, {@code execucao:beneficiario:ler_integral}, fora do escopo desta borda de escrita).
     */
    record BeneficiarioResponse(String nome, String cpfCnpj) {

        static BeneficiarioResponse de(Beneficiario beneficiario) {
            return new BeneficiarioResponse(beneficiario.nome(), new Cpf(beneficiario.cpfCnpj()).mascarado());
        }
    }

    record PagamentoRequest(
            UUID liquidacaoId,
            LocalDate dataCompetencia,
            String valor,
            String natureza,
            BeneficiarioRequest beneficiario,
            String ordemBancaria,
            String historico) {}

    record PagamentoResponse(
            UUID id,
            UUID liquidacaoId,
            LocalDate dataCompetencia,
            String valor,
            String natureza,
            BeneficiarioResponse beneficiario,
            String ordemBancaria,
            String historico,
            UUID fatoContabilId) {

        static PagamentoResponse de(Pagamento pagamento) {
            return new PagamentoResponse(
                    pagamento.id().valor(),
                    pagamento.liquidacaoId().valor(),
                    pagamento.dataCompetencia(),
                    pagamento.valor().valor().toPlainString(),
                    pagamento.natureza().name().toLowerCase(),
                    pagamento.beneficiario().map(BeneficiarioResponse::de).orElse(null),
                    pagamento.ordemBancaria().orElse(null),
                    pagamento.historico(),
                    pagamento.fatoContabilId().valor());
        }
    }

    /** Item do lote (RAZ-79 §6.5): mesmos campos de {@link PagamentoRequest}, achatados com {@code chaveCliente}. */
    record ItemLotePagamentoRequest(
            String chaveCliente,
            UUID liquidacaoId,
            LocalDate dataCompetencia,
            String valor,
            String natureza,
            BeneficiarioRequest beneficiario,
            String ordemBancaria,
            String historico) {

        PagamentoRequest paraPagamentoRequest() {
            return new PagamentoRequest(liquidacaoId, dataCompetencia, valor, natureza, beneficiario, ordemBancaria, historico);
        }
    }

    record LotePagamentoRequest(List<ItemLotePagamentoRequest> itens) {}

    record ItemProcessado(String chaveCliente, UUID id, UUID fatoContabilId) {}

    record ItemComErro(String chaveCliente, String codigo, String mensagem) {}

    record LotePagamentoResponse(List<ItemProcessado> processados, List<ItemComErro> errors) {}
}
