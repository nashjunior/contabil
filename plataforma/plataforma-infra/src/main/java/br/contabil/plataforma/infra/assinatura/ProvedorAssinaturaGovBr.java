package br.contabil.plataforma.infra.assinatura;

import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelAssinatura;

/**
 * Abstração de provedor (ADR-0008) para a API de Assinatura Eletrônica gov.br —
 * avançada em F0 e qualificada ICP-Brasil em F1. Representa uma sessão já autenticada
 * no nível necessário (o token OAuth2 obtido com escopo {@code sign} ou
 * {@code signature_session}, e opcionalmente {@code icp_brasil}, obtido
 * pelo fluxo interativo de autorização do signatário é responsabilidade de
 * quem constrói/injeta a implementação — RAZ-11 não inclui o fluxo web de
 * autorização, ver package-info).
 *
 * <p>Operação e formato alinhados ao manual oficial (Roteiro de Integração API
 * Assinatura avançada gov.br,
 * https://manual-integracao-assinatura-eletronica.servicos.gov.br/). Não existe
 * endpoint de elegibilidade separado: conta Bronze ou CPF com situação que
 * impede o uso já respondem 403 na própria operação de assinatura — é assim
 * que este contrato expõe a checagem "antes de assinar" do item 6 da spec.
 */
public interface ProvedorAssinaturaGovBr {

    /**
     * Assina o hash SHA-256 informado, devolvendo o pacote PKCS#7 destacado
     * (.p7s) — operação {@code assinarPKCS7} da API gov.br. O certificado do
     * signatário vem embutido no {@code SignedData} do PKCS#7 devolvido (não é
     * preciso uma chamada separada para obtê-lo — ver {@link ExtratorCertificadoPkcs7}).
     *
     * @throws ContaGovBrNaoElegivelException conta Bronze ou CPF com situação que impede a
     *     assinatura (a API gov.br responde 403 nesses casos)
     */
    default byte[] assinarPkcs7(byte[] hashSha256) {
        return assinarPkcs7(hashSha256, NivelAssinatura.AVANCADA_GOVBR);
    }

    byte[] assinarPkcs7(byte[] hashSha256, NivelAssinatura nivel);

    /**
     * Erro {@code 403} da API gov.br quando a conta não tem o nível (Prata/Ouro)
     * exigido, ou o CPF está com situação (falecido/cancelado/nulo) que impede o
     * uso da assinatura eletrônica.
     */
    final class ContaGovBrNaoElegivelException extends RuntimeException {
        public ContaGovBrNaoElegivelException(String mensagem) {
            super(mensagem);
        }
    }
}
