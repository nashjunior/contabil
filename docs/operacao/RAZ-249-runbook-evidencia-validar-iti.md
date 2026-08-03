# RAZ-249 — Runbook: evidência de conformidade VALIDAR/ITI (assinatura qualificada)

Procedimento operacional de **evidência de conformidade** da assinatura qualificada
ICP-Brasil, fora do domínio Java. Autoridade de decisão:
[ADR-0058](../arquitetura-tecnica/adr/0058-conformidade-validar-iti-evidencia-operacional.md)
(conformidade VALIDAR/ITI é evidência operacional), sobre
[ADR-0008](../arquitetura-tecnica/adr/0008-assinatura-provedor.md) (abstração de
provedor) e [ADR-0009](../arquitetura-tecnica/adr/0009-documentos-object-store.md)/[ADR-0018](../arquitetura-tecnica/adr/0018-object-store-s3-compativel.md)
(object store/GED). Base legal: MP 2.200-2/2001 (ICP-Brasil, presunção de
veracidade), Lei 14.063/2020 (assinatura em atos públicos), Decreto 10.540/2020
(SIAFIC — rastreabilidade).

Este runbook é a **evidência operacional mínima para auditoria TCE/ANPD** da
conformidade da assinatura qualificada — o análogo, para assinatura, do que a
[F0-runbook](./F0-runbook-tls-backup-restauracao.md) é para TLS/backup e do que
RAZ-38 é para a evidência operacional do ente-piloto.

**Owner do desbloqueio: ENTE-PILOTO/OWNER.**

---

## 1. Por que isto não é código

O serviço público do ITI (`validar.iti.gov.br`) valida assinaturas por **upload de
arquivo ou QR Code** e emite um **relatório de conformidade legível por humano**.
**Não há API servidor-a-servidor estável** para automatizar a chamada no adapter
(mesma restrição do SICONFI — [ADR-0049](../arquitetura-tecnica/adr/0049-submissao-siconfi-passo-assistido.md)).
Por isso o código para na **checagem local**: `ServicoAssinaturaGovBrAvancada#verificar`
confere integridade + cadeia + revogação (OCSP/CRL) e registra na trilha
(`ServicoAssinaturaGovBrAvancada.java:151-215`); a conformidade VALIDAR é
**evidência operacional externa**, não integração. A primeira evidência exige uma
assinatura **qualificada ICP-Brasil real**, que só existe no ambiente do ente.

## 2. O que produzir (primeira evidência)

1. Assinar um documento real no nível **qualificada ICP-Brasil** (escopo
   `icp_brasil`, caminho já implementado em RAZ-208) — ex.: uma nota de empenho de
   ordenador que o ente decidiu elevar a qualificada, ou um contrato/portaria.
   Guardar o **`idTransacao`**, o **hash SHA-256** e a **URI do PDF assinado** no
   object store (as três chaves já gravadas pelo evento `assinatura_eletronica` na
   trilha — `ServicoAssinaturaGovBrAvancada.java:201-215`).
2. Acessar `https://validar.iti.gov.br`, fazer **upload do PDF assinado** (ou ler o
   QR Code, quando presente) e emitir o **relatório de conformidade** do VALIDAR.
3. Conferir no relatório: **cadeia ICP-Brasil íntegra**, **status de revogação** no
   momento da assinatura, **integridade do documento** (hash conferido) e o **tipo
   de assinatura** reconhecido (PAdES). O relatório deve **bater** com o resultado
   local de `verificar(...)` — divergência abre incidente, não vira evidência.

## 3. Como anexar à trilha de auditoria do documento

- **Depositar o relatório VALIDAR no object store/GED** ([ADR-0009](../arquitetura-tecnica/adr/0009-documentos-object-store.md)/[ADR-0018](../arquitetura-tecnica/adr/0018-object-store-s3-compativel.md),
  cifrado em repouso e coberto pelo backup), **nomeado/rotulado pelo `idTransacao`**
  do documento assinado — é o `idTransacao` que amarra o relatório ao evento
  `assinatura_eletronica` já existente na trilha imutável.
- **Listar no dossiê de aceite** do ente: `idTransacao`, hash SHA-256, URI do PDF
  assinado, URI do relatório VALIDAR, data-hora da validação e veredito. Dados
  **não sensíveis** (mesma regra do dossiê de RAZ-39): nada de segredo, nada de PII
  em claro — CPF do signatário fica mascarado, como já na trilha.
- **Automação futura (opcional, fora do escopo F1):** se/quando houver demanda, o
  relatório pode ser capturado como um `EventoAuditoria` de tipo
  `conformidade_validar_iti` **anexado à hash-chain** (reusa `AuditoriaEscrita#append`,
  referencia a mesma URI/`idTransacao`), preservando o append-only ([ADR-0005](../arquitetura-tecnica/adr/0005-trilha-append-only-hash-chain.md)).
  Não exigido para fechar RAZ-249 — a captura em F1 é o depósito + dossiê acima.

## 4. Cadência

- **Primeira evidência:** no go-live do ente-piloto, sobre o primeiro documento
  assinado em nível qualificada (destrava RAZ-249 → RAZ-246 → resíduo de RAZ-208).
- **Amostragem periódica:** repetir sobre uma amostra de documentos qualificados a
  cada ciclo de auditoria, ou a cada **rotação de CA da ICP-Brasil** (ver
  [RAZ-24 runbook](./RAZ-24-runbook-icp-brasil-trust-anchors.md)), para atestar que
  a cadeia provisionada continua conforme o validador oficial.

## 5. Dossiê de aceite — evidência VALIDAR/ITI (RAZ-249)

- [ ] Documento real assinado em nível **qualificada ICP-Brasil** (`idTransacao`,
      hash, URI registrados — seção 2).
- [ ] **Relatório de conformidade do VALIDAR/ITI** emitido, com cadeia íntegra,
      revogação conferida e integridade OK (seção 2).
- [ ] Relatório VALIDAR **depositado no object store** e amarrado ao `idTransacao`
      do documento (seção 3).
- [ ] Linha no **dossiê de aceite** do ente com as chaves não sensíveis (seção 3).
- [ ] Divergência local × VALIDAR, se houver, tratada como **incidente** — não
      arquivada como evidência de conformidade (seção 2).

---

[← Operação](./README.md) · [ADR-0058 Conformidade VALIDAR/ITI](../arquitetura-tecnica/adr/0058-conformidade-validar-iti-evidencia-operacional.md) · [RAZ-24 âncoras ICP-Brasil](./RAZ-24-runbook-icp-brasil-trust-anchors.md) · [Transversais/01 Assinatura](../transversais/01-assinatura-eletronica.md)
