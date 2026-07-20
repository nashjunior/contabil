# RAZ-24 — Runbook: âncoras de confiança ICP-Brasil (verificação de revogação)

Provisionamento operacional exigido para ligar `ServicoAssinaturaGovBrConfiguration`
(RAZ-24) — a composição real de [`ServicoAssinaturaGovBrAvancada`](../../plataforma/plataforma-infra/src/main/java/br/contabil/plataforma/infra/assinatura/ServicoAssinaturaGovBrAvancada.java).
Autoridade de domínio: [ADR-0008](../arquitetura-tecnica/adr/0008-assinatura-provedor.md)
(abstração de provedor de assinatura), [ADR-0017](../arquitetura-tecnica/adr/0017-bff-oauth-assinatura-govbr.md)
(BFF/OAuth2 do signatário). Base legal: Lei 14.063/2020 (assinatura eletrônica em
atos públicos), MP 2.200-2/2001 (ICP-Brasil).

## Por que isto não é código

`VerificadorRevogacaoCertificadoPkix` (checagem OCSP/CRL via `CertPathValidator("PKIX")`
do JDK) precisa de um conjunto de `TrustAnchor` — os certificados raiz/intermediários
da cadeia ICP-Brasil em que o sistema confia para validar o certificado do signatário.
Esse bundle **não é embutido no código-fonte**: a ICP-Brasil roda rotação de CA por
política própria (AC-Raiz, ACs intermediárias), e um bundle desatualizado faz o
sistema rejeitar (deny-by-default) certificados legítimos assinados por uma CA nova,
ou pior, aceitar uma CA revogada se o bundle nunca for atualizado. Isto é
provisionamento de infraestrutura, análogo ao bundle de CAs do sistema operacional —
nunca gerado nem inventado por um agente de código.

## O que provisionar

1. Baixar o bundle oficial de certificados da cadeia ICP-Brasil (AC-Raiz +
   ACs intermediárias relevantes ao nível exigido — avançada, gov.br) do
   repositório oficial do ITI (Instituto Nacional de Tecnologia da Informação):
   `https://www.gov.br/iti/pt-br/assuntos/repositorio`. **Staging e produção usam
   o mesmo bundle** — a cadeia de CA não muda por ambiente, só o servidor de
   assinatura (`assinatura-api.staging.iti.br` vs. produção).
2. Concatenar os certificados `.crt`/`.pem` baixados em um único arquivo PEM
   (múltiplos blocos `-----BEGIN CERTIFICATE-----`/`-----END CERTIFICATE-----`
   no mesmo arquivo — `AncorasConfiancaIcpBrasil.carregar` aceita um bundle
   com N certificados).
3. Depositar o arquivo no caminho apontado por
   `siafic.assinatura.icp-brasil.trust-store-pem` (variável de ambiente
   sugerida: `ICP_BRASIL_TRUST_STORE_PEM`, análoga ao padrão já usado em
   `siafic.assinatura.govbr.oauth.*`). **Fora do repositório** — mesma regra de
   segredos/material de confiança que já vale para chaves e credenciais.
4. Rotação: repetir os passos 1–3 quando o ITI publicar rotação de CA. Não há
   automação de rotação nesta entrega (F0) — é checagem manual periódica,
   registrada aqui como pendência de operação, não de código.

## Como conferir

- Sem o arquivo configurado, a subida do contexto Spring falha rápido
  (`IllegalStateException`, mensagem aponta para este runbook) — nunca sobe
  com `ServicoAssinaturaGovBrConfiguration` ligado (`siafic.assinatura.govbr.enabled=true`)
  e verificação de revogação ausente ou vazia.
- `openssl crl2pkcs7 -nocrl -certfile <bundle.pem> | openssl pkcs7 -print_certs -noout`
  lista os certificados do bundle — confirmar que a cadeia esperada (AC-Raiz +
  intermediárias) está presente antes de configurar o caminho.

## Pré-requisito adicional para RAZ-24 completa

`ServicoAssinaturaGovBrConfiguration` também exige `contabil.objectstore.enabled=true`
([ADR-0018](../arquitetura-tecnica/adr/0018-object-store-s3-compativel.md)) — sem
isso a subida falha por dependência ausente (`Function`/`BiFunction`/`Consumer` dos
seams de object store). Credenciais OAuth2 gov.br staging: RAZ-39 (concluída, ver
comentários da issue — dossiê de aceite não commitado ainda neste repo).
