package br.contabil.execucao.domain;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado de liquidação da despesa: atesta/verifica o direito adquirido e gera
 * um fato contábil append-only no razão.
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

    private Liquidacao(
            LiquidacaoId id,
            TenantId enteId,
            EmpenhoId empenhoId,
            LocalDate dataCompetencia,
            Dinheiro valor,
            List<DocumentoSuporte> documentosSuporte,
            String historico,
            UUID fatoContabilId) {
        this.id = id;
        this.enteId = enteId;
        this.empenhoId = empenhoId;
        this.dataCompetencia = dataCompetencia;
        this.valor = valor;
        this.documentosSuporte = documentosSuporte;
        this.historico = historico;
        this.fatoContabilId = fatoContabilId;
    }

    public static Liquidacao registrar(
            LiquidacaoId id,
            TenantId enteId,
            EmpenhoId empenhoId,
            LocalDate dataCompetencia,
            Dinheiro valor,
            List<DocumentoSuporte> documentosSuporte,
            String historico,
            UUID fatoContabilId) {
        validarEntrada(valor, documentosSuporte, historico);
        return new Liquidacao(
                Objects.requireNonNull(id, "id não pode ser nulo"),
                Objects.requireNonNull(enteId, "enteId não pode ser nulo"),
                Objects.requireNonNull(empenhoId, "empenhoId não pode ser nulo"),
                Objects.requireNonNull(dataCompetencia, "dataCompetencia não pode ser nula"),
                valor,
                List.copyOf(documentosSuporte),
                textoObrigatorio(historico, "histórico"),
                Objects.requireNonNull(fatoContabilId, "fatoContabilId não pode ser nulo"));
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
}
