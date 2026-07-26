package br.contabil.plataforma.domain.mascaramento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.Audiencia;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.BaseLegalLgpd;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.CampoSensivel;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.Categoria;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.ContextoAcesso;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServicoMascaramentoPadraoTest {

    private static final TenantId ENTE = TenantId.de(UUID.randomUUID().toString());
    private static final ContextoAcesso CONTEXTO_PUBLICO =
            new ContextoAcesso(ENTE, "portal-publico", "transparencia_ativa", BaseLegalLgpd.OBRIGACAO_LEGAL);

    private final ServicoMascaramento servico = new ServicoMascaramentoPadrao();

    @Test
    @DisplayName("CPF no portal público expõe somente os 3 dígitos centrais")
    void mascaraCpfNoPortalPublico() {
        CampoSensivel cpf = new CampoSensivel("cpf", "11122233344", Categoria.CPF);

        String resultado = servico.mascarar(cpf, CONTEXTO_PUBLICO, Audiencia.PORTAL_PUBLICO);

        assertThat(resultado).isEqualTo("***.222.***-**");
        assertThat(resultado).doesNotContain("111").doesNotContain("33344");
    }

    @Test
    @DisplayName("CPF malformado falha fechado sem devolver o valor em claro")
    void cpfMalformadoNaoVaza() {
        CampoSensivel cpf = new CampoSensivel("cpf", "doc-invalido-abc", Categoria.CPF);

        String resultado = servico.mascarar(cpf, CONTEXTO_PUBLICO, Audiencia.PORTAL_PUBLICO);

        assertThat(resultado).isEqualTo("***").doesNotContain("doc-invalido");
    }

    @Test
    @DisplayName("portal público suprime campos que a LGPD não permite expor")
    void suprimeCamposNaoPublicos() {
        assertThat(portal("endereco", "Rua Fixture, 123", Categoria.ENDERECO)).isEqualTo("[SUPRIMIDO]");
        assertThat(portal("banco", "agencia 0001 conta 9999", Categoria.DADO_BANCARIO)).isEqualTo("[SUPRIMIDO]");
        assertThat(portal("telefone", "telefone pessoal 9999", Categoria.CONTATO)).isEqualTo("[SUPRIMIDO]");
        assertThat(portal("biometria", "hash biometrico", Categoria.DADO_SENSIVEL)).isEqualTo("[SUPRIMIDO]");
        assertThat(portal("rg", "registro geral", Categoria.OUTRO)).isEqualTo("[SUPRIMIDO]");
    }

    @Test
    @DisplayName("nome permanece publicável no portal quando há base legal")
    void nomePublicoPermitido() {
        assertThat(portal("nome", "Maria Servidora", Categoria.NOME)).isEqualTo("Maria Servidora");
    }

    @Test
    @DisplayName("audiência interna autorizada recebe valor integral sob base legal")
    void audienciaInternaRecebeValorIntegral() {
        CampoSensivel dadoBancario = new CampoSensivel("banco", "agencia 0001 conta 9999", Categoria.DADO_BANCARIO);

        String resultado = servico.mascarar(dadoBancario, CONTEXTO_PUBLICO, Audiencia.BACKOFFICE);

        assertThat(resultado).isEqualTo("agencia 0001 conta 9999");
    }

    @Test
    @DisplayName("sem finalidade de tratamento a política falha fechada")
    void semFinalidadeFalhaFechado() {
        ContextoAcesso semFinalidade =
                new ContextoAcesso(ENTE, "portal-publico", " ", BaseLegalLgpd.OBRIGACAO_LEGAL);
        CampoSensivel nome = new CampoSensivel("nome", "Maria Servidora", Categoria.NOME);

        assertThatThrownBy(() -> servico.mascarar(nome, semFinalidade, Audiencia.PORTAL_PUBLICO))
                .isInstanceOf(ServicoMascaramento.SemBaseLegalException.class);
    }

    private String portal(String nome, String valor, Categoria categoria) {
        return servico.mascarar(new CampoSensivel(nome, valor, categoria), CONTEXTO_PUBLICO, Audiencia.PORTAL_PUBLICO);
    }
}
