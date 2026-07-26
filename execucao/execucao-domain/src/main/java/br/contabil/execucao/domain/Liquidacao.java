package br.contabil.execucao.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;

/**
 * Agregado de liquidação da despesa: atesta/verifica o direito adquirido e gera
 * um fato contábil append-only no razão.
 *
 * <p>{@code statusAprovacao} é o único pedaço de estado forte do agregado
 * (ADR-0023): transiciona só via {@link #aprovar} / {@link #devolver}, nunca
 * por saldo — os demais rótulos do contrato (§6.4, {@code
 * registrada|paga_parcial|paga_total}) são leitura derivada de fora deste
 * agregado.
 */
public final class Liquidacao {

    private final LiquidacaoId id;
    private final TenantId enteId;
    private final EmpenhoId empenhoId;
    private final LocalDate dataCompetencia;
    private final Dinheiro valor;
    private final List<DocumentoSuporte> documentosSuporte;
    private final String historico;
    private final UUID fatoContabilId;
    private final Cpf autor;
    private final StatusAprovacao statusAprovacao;
    private final Optional<Cpf> aprovador;
    private final Optional<String> motivoDevolucao;

    private Liquidacao(
            LiquidacaoId id,
            TenantId enteId,
            EmpenhoId empenhoId,
            LocalDate dataCompetencia,
            Dinheiro valor,
            List<DocumentoSuporte> documentosSuporte,
            String historico,
            UUID fatoContabilId,
            Cpf autor,
            StatusAprovacao statusAprovacao,
            Optional<Cpf> aprovador,
            Optional<String> motivoDevolucao) {
        this.id = id;
        this.enteId = enteId;
        this.empenhoId = empenhoId;
        this.dataCompetencia = dataCompetencia;
        this.valor = valor;
        this.documentosSuporte = documentosSuporte;
        this.historico = historico;
        this.fatoContabilId = fatoContabilId;
        this.autor = autor;
        this.statusAprovacao = statusAprovacao;
        this.aprovador = aprovador;
        this.motivoDevolucao = motivoDevolucao;
    }

    public static Liquidacao registrar(
            LiquidacaoId id,
            TenantId enteId,
            EmpenhoId empenhoId,
            LocalDate dataCompetencia,
            Dinheiro valor,
            List<DocumentoSuporte> documentosSuporte,
            String historico,
            UUID fatoContabilId,
            Cpf autor) {
        validarEntrada(valor, documentosSuporte, historico);
        return new Liquidacao(
                Objects.requireNonNull(id, "id não pode ser nulo"),
                Objects.requireNonNull(enteId, "enteId não pode ser nulo"),
                Objects.requireNonNull(empenhoId, "empenhoId não pode ser nulo"),
                Objects.requireNonNull(dataCompetencia, "dataCompetencia não pode ser nula"),
                valor,
                List.copyOf(documentosSuporte),
                textoObrigatorio(historico, "histórico"),
                Objects.requireNonNull(fatoContabilId, "fatoContabilId não pode ser nulo"),
                Objects.requireNonNull(autor, "autor não pode ser nulo"),
                StatusAprovacao.PENDENTE,
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Reconstitui uma liquidação já persistida, com a decisão de aprovação que já foi validada na
     * escrita (RAZ-92) — ao contrário de {@link #aprovar}/{@link #devolver}, não reaplica a checagem
     * de auto-aprovação (ela já rodou quando a decisão foi tomada; reaplicá-la na leitura exigiria
     * reconstruir o autor do empenho da cadeia só para uma validação redundante). Uso exclusivo dos
     * adapters de persistência (RAZ-105, {@code LiquidacaoRepository}).
     */
    public static Liquidacao reidratar(
            LiquidacaoId id,
            TenantId enteId,
            EmpenhoId empenhoId,
            LocalDate dataCompetencia,
            Dinheiro valor,
            List<DocumentoSuporte> documentosSuporte,
            String historico,
            UUID fatoContabilId,
            Cpf autor,
            StatusAprovacao statusAprovacao,
            Optional<Cpf> aprovador,
            Optional<String> motivoDevolucao) {
        return new Liquidacao(
                Objects.requireNonNull(id, "id não pode ser nulo"),
                Objects.requireNonNull(enteId, "enteId não pode ser nulo"),
                Objects.requireNonNull(empenhoId, "empenhoId não pode ser nulo"),
                Objects.requireNonNull(dataCompetencia, "dataCompetencia não pode ser nula"),
                valor,
                List.copyOf(documentosSuporte),
                textoObrigatorio(historico, "histórico"),
                Objects.requireNonNull(fatoContabilId, "fatoContabilId não pode ser nulo"),
                Objects.requireNonNull(autor, "autor não pode ser nulo"),
                Objects.requireNonNull(statusAprovacao, "statusAprovacao não pode ser nulo"),
                Objects.requireNonNull(aprovador, "aprovador não pode ser nulo"),
                Objects.requireNonNull(motivoDevolucao, "motivoDevolucao não pode ser nulo"));
    }

    /**
     * Transiciona {@code PENDENTE -> APROVADA} (ADR-0023). Recusa auto-aprovação:
     * {@code aprovador} não pode ser o autor desta liquidação nem o autor do
     * empenho da mesma cadeia (Regra 9).
     */
    public Liquidacao aprovar(Cpf aprovador, Cpf autorEmpenho) {
        exigirPendente();
        exigirNaoAutoAprovacao(aprovador, autorEmpenho);
        return new Liquidacao(
                id, enteId, empenhoId, dataCompetencia, valor, documentosSuporte, historico, fatoContabilId,
                autor, StatusAprovacao.APROVADA, Optional.of(aprovador), Optional.empty());
    }

    /**
     * Transiciona {@code PENDENTE -> DEVOLVIDA} (ADR-0023). Mesma recusa de
     * auto-aprovação de {@link #aprovar}; exige motivo não vazio.
     */
    public Liquidacao devolver(Cpf aprovador, Cpf autorEmpenho, String motivo) {
        exigirPendente();
        exigirNaoAutoAprovacao(aprovador, autorEmpenho);
        if (motivo == null || motivo.isBlank()) {
            throw new ExecucaoInvalidaException(
                    "motivo_devolucao_obrigatorio", "motivo é obrigatório ao devolver a liquidação");
        }
        return new Liquidacao(
                id, enteId, empenhoId, dataCompetencia, valor, documentosSuporte, historico, fatoContabilId,
                autor, StatusAprovacao.DEVOLVIDA, Optional.of(aprovador), Optional.of(motivo));
    }

    private void exigirPendente() {
        if (statusAprovacao != StatusAprovacao.PENDENTE) {
            throw new ExecucaoInvalidaException(
                    "liquidacao_ja_decidida",
                    "liquidação %s já foi decidida (status atual: %s)".formatted(id.valor(), statusAprovacao));
        }
    }

    private void exigirNaoAutoAprovacao(Cpf aprovador, Cpf autorEmpenho) {
        Objects.requireNonNull(aprovador, "aprovador não pode ser nulo");
        if (aprovador.equals(autor)) {
            throw new AutoAprovacaoNaoPermitidaException("liquidação");
        }
        if (aprovador.equals(autorEmpenho)) {
            throw new AutoAprovacaoNaoPermitidaException("empenho");
        }
    }

    public static void validarEntrada(Dinheiro valor, List<DocumentoSuporte> documentosSuporte, String historico) {
        validarValor(valor, "valor da liquidação");
        validarDocumentosSuporte(documentosSuporte);
        textoObrigatorio(historico, "histórico");
    }

    public static void validarValor(Dinheiro valor, String campo) {
        Objects.requireNonNull(valor, campo + " não pode ser nulo");
        if (valor.compareTo(Dinheiro.zero()) <= 0) {
            throw new ExecucaoInvalidaException("valor_invalido", campo + " deve ser positivo");
        }
    }

    public static void validarDocumentosSuporte(List<DocumentoSuporte> documentosSuporte) {
        Objects.requireNonNull(documentosSuporte, "documentosSuporte não pode ser nulo");
        if (documentosSuporte.isEmpty()) {
            throw new ExecucaoInvalidaException(
                    "documento_suporte_obrigatorio", "liquidação exige ao menos um documento de suporte");
        }
    }

    static String textoObrigatorio(String valor, String campo) {
        Objects.requireNonNull(valor, campo + " não pode ser nulo");
        if (valor.isBlank()) {
            throw new IllegalArgumentException(campo + " não pode ser vazio");
        }
        return valor;
    }

    public LiquidacaoId id() {
        return id;
    }

    public TenantId enteId() {
        return enteId;
    }

    public EmpenhoId empenhoId() {
        return empenhoId;
    }

    public LocalDate dataCompetencia() {
        return dataCompetencia;
    }

    public Dinheiro valor() {
        return valor;
    }

    public List<DocumentoSuporte> documentosSuporte() {
        return documentosSuporte;
    }

    public String historico() {
        return historico;
    }

    public UUID fatoContabilId() {
        return fatoContabilId;
    }

    /** CPF de quem registrou a liquidação — checado pelo gate de aprovação (ADR-0023). */
    public Cpf autor() {
        return autor;
    }

    public StatusAprovacao statusAprovacao() {
        return statusAprovacao;
    }

    public Optional<Cpf> aprovador() {
        return aprovador;
    }

    public Optional<String> motivoDevolucao() {
        return motivoDevolucao;
    }
}
