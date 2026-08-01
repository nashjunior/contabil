# ADR-0049 · Submissão ao SICONFI é passo assistido (empacotamento + upload web com e-CPF A3 ICP-Brasil; sem integração M2M de escrita)

- **Status:** Aceita
- **Data:** 2026-08-01
- **Contexto:** A prestação de contas (RAZ-209, [docs/16](../../16-prestacao-de-contas.md)) precisa entregar a MSC ([ADR-0048](./0048-msc-contrato-unico-siconfi.md)) ao SICONFI. A pesquisa de fontes primárias confirma que **não há API pública de escrita** do SICONFI: a homologação exige **upload web + assinatura com e-CPF A3 ICP-Brasil** do responsável. Existe apenas uma **API de consulta** (`apidatalake.tesouro.gov.br/ords/siconfi/tt/`, JSON, read-only, sem autenticação). O produto já tem a abstração de provedor de assinatura ([ADR-0008](./0008-assinatura-provedor.md): gov.br → ICP-Brasil) e o delta de e-CPF A3 está sendo fechado em RAZ-208.
- **Decisão:**
  - **O SIAFIC gera e valida o pacote; a submissão é passo assistido/manual — não integração máquina-a-máquina de envio.** O produto produz o artefato (**CSV Anexo II e/ou XBRL GL, zipado**), roda o validador local espelhando o SICONFI ([ADR-0048](./0048-msc-contrato-unico-siconfi.md)) e entrega o pacote pronto + trilha; o operador faz o upload no portal do SICONFI e assina com e-CPF A3.
  - **Assinatura reusa a abstração de provedor** ([ADR-0008](./0008-assinatura-provedor.md)) — o e-CPF A3 ICP-Brasil exigido para homologar conecta ao delta de RAZ-208; não se cria caminho de assinatura paralelo.
  - **Conciliação/monitoramento via a API de consulta** (read-only) do apidatalake: reconciliar o que foi aceito/homologado e monitorar entregas/prazos, fechando o loop sem depender de uma API de escrita inexistente.
- **Consequências:** o produto não fica acoplado a uma API que não existe; entrega um pacote válido e auditável e deixa o passo humano (upload + assinatura) explícito no runbook operacional. A conciliação read-only dá visibilidade do status pós-envio. Depende de RAZ-208 (e-CPF A3) para o passo de assinatura na homologação.
- **Alternativas consideradas:**
  - **Integração M2M de envio ao SICONFI** — impossível: não existe API oficial de escrita; qualquer promessa de envio automático seria falsa.
  - **Automação por scraping/robô do portal** — rejeitada: frágil, sem contrato estável, sujeita a quebra a cada mudança do portal e a risco de violar termos de uso; o ganho não compensa o passo humano de assinatura A3, que exige o token/certificado do responsável de todo modo.

---

[← ADRs](./README.md) · [ADR-0008 Assinatura via provedor](./0008-assinatura-provedor.md) · [ADR-0048 MSC contrato único](./0048-msc-contrato-unico-siconfi.md) · [Spec docs/16](../../16-prestacao-de-contas.md) · [Transversais/01 Assinatura](../../transversais/01-assinatura-eletronica.md)
