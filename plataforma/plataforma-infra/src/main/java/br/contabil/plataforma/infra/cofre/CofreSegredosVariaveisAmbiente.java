package br.contabil.plataforma.infra.cofre;

import br.contabil.plataforma.domain.cofre.CofreSegredos;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Adapter local/dev para o port {@link CofreSegredos}. Em produção, a mesma porta
 * deve ser implementada por Secrets Manager/Vault/HSM; este adapter preserva o contrato
 * de referencia logica e impede fallback literal em config.
 */
public final class CofreSegredosVariaveisAmbiente implements CofreSegredos {

    private static final Duration VALIDADE_PADRAO = Duration.ofMinutes(15);
    private final Function<String, String> ambiente;
    private final Clock clock;
    private final Duration validade;

    public CofreSegredosVariaveisAmbiente(Function<String, String> ambiente, Clock clock, Duration validade) {
        this.ambiente = Objects.requireNonNull(ambiente, "ambiente");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.validade = Objects.requireNonNullElse(validade, VALIDADE_PADRAO);
        if (this.validade.isNegative() || this.validade.isZero()) {
            throw new IllegalArgumentException("validade do segredo deve ser positiva");
        }
    }

    public static CofreSegredosVariaveisAmbiente sistema() {
        return new CofreSegredosVariaveisAmbiente(System::getenv, Clock.systemUTC(), VALIDADE_PADRAO);
    }

    @Override
    public ValorSegredo obter(ContaServico conta, ReferenciaSegredo segredo) {
        Objects.requireNonNull(conta, "conta");
        Objects.requireNonNull(segredo, "segredo");
        exigirEscopo(conta, segredo);
        String variavel = nomeVariavel(segredo);
        String valor = ambiente.apply(variavel);
        if (valor == null || valor.isBlank()) {
            throw new SemEscopoException("segredo sem valor acessivel no cofre: " + segredo.caminho());
        }
        return new ValorSegredo(valor.toCharArray(), clock.instant().plus(validade));
    }

    @Override
    public void rotacionar(ReferenciaSegredo segredo) {
        throw new UnsupportedOperationException("rotacao deve ocorrer no provedor KMS/HSM/cofre gerenciado");
    }

    static String nomeVariavel(ReferenciaSegredo segredo) {
        String caminho = segredo.caminho();
        if (caminho.startsWith("env:")) {
            String explicita = caminho.substring("env:".length()).trim();
            if (explicita.isBlank()) {
                throw new IllegalArgumentException("referencia env: deve informar a variavel");
            }
            return explicita;
        }
        String logico = caminho
                .replaceFirst("^cofre://", "")
                .replaceFirst("^/+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (logico.isBlank()) {
            throw new IllegalArgumentException("referencia de segredo invalida: " + caminho);
        }
        return logico;
    }

    private static void exigirEscopo(ContaServico conta, ReferenciaSegredo segredo) {
        if (conta.escopos().isEmpty() || conta.escopos().contains("cofre:*")) {
            return;
        }
        boolean permitido = conta.escopos().stream()
                .anyMatch(escopo -> segredo.caminho().equals(escopo) || segredo.caminho().startsWith(escopo + "/"));
        if (!permitido) {
            throw new SemEscopoException(
                    "conta " + conta.nome() + " nao possui escopo para " + segredo.caminho());
        }
    }
}
