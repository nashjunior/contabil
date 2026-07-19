package br.contabil.plataforma.domain.iam;

import static org.assertj.core.api.Assertions.assertThat;

import br.contabil.plataforma.domain.iam.ServicoIdentidade.Credencial;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.CredencialCertificadoIcp;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.CredencialGovBr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guardrail RAZ-33: no MVP não existe login por senha local — identidade é
 * sempre federada por claim verificada (gov.br SSO / certificado ICP-Brasil),
 * nunca por usuário/senha próprio (docs/05-regras-de-negocio.md Regra 7:
 * "vedada a criação de usuários genéricos"; docs/13-nfr §Piso de segurança
 * F0: hash Argon2id/bcrypt/scrypt+salt só se e quando houver senha local).
 *
 * <p>{@link Credencial} é {@code sealed} com {@code permits} fechado em
 * {@link CredencialGovBr} e {@link CredencialCertificadoIcp} — travado em
 * tempo de COMPILAÇÃO: nenhum tipo de credencial baseada em senha pode ser
 * adicionado ao contrato sem tocar (e revisar) este arquivo. Este teste prova
 * a mesma invariante em tempo de execução/CI, para não depender só de quem lê
 * o código-fonte perceber o {@code sealed}.
 *
 * <p>Se algum dia o produto precisar de senha local, este teste deve ser
 * substituído por uma política real (Argon2id/bcrypt/scrypt com salt — nunca
 * hash simples), nunca apenas relaxado.
 */
class SemAutenticacaoPorSenhaLocalTest {

    @Test
    @DisplayName("Credencial (ServicoIdentidade) permite só gov.br e certificado ICP-Brasil — nenhum tipo baseado em senha")
    void credencialNaoTemVarianteDeSenha() {
        Class<?>[] permitidas = Credencial.class.getPermittedSubclasses();

        assertThat(permitidas)
                .as("qualquer novo tipo de Credencial precisa ser avaliado aqui — "
                        + "se for baseado em senha, exige política Argon2id/bcrypt/scrypt+salt (13-nfr §piso F0)")
                .containsExactlyInAnyOrder(CredencialGovBr.class, CredencialCertificadoIcp.class);

        assertThat(permitidas)
                .as("nome de nenhuma credencial pode sugerir senha/password local")
                .noneMatch(tipo -> tipo.getSimpleName().toLowerCase().contains("senha")
                        || tipo.getSimpleName().toLowerCase().contains("password"));
    }
}
