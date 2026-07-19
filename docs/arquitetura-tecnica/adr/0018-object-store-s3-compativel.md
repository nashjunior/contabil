# ADR-0018 · Object store S3-compatível (AWS SDK v2 / MinIO), cifrado, referência por URI

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** O [ADR-0009](./0009-documentos-object-store.md) decidiu o **quê** — documentos vivem em object store/GED cifrado, referenciados por URI a partir do fato (`DOCUMENTO_ASSINADO`), nunca BLOB no banco — mas não a tecnologia. O fluxo de assinatura (`ServicoAssinaturaGovBrAvancada`, RAZ-11/24) recebe leitura/gravação do documento como colaboradores injetados (`Function<ReferenciaDocumento,byte[]>` / `BiFunction<byte[],ReferenciaDocumento,ReferenciaDocumento>`) porque não havia adaptador concreto. Esta decisão fixa o **como** (RAZ-32).
- **Decisão:**
  - **API S3-compatível via AWS SDK v2 (`S3Client`)**, cliente HTTP JDK (`url-connection-client`). Uma só API roda em nuvem pública (AWS S3) e **on-prem via MinIO** — atende entes que exigem infra própria por soberania de dados e serve dev/CI local. Dependência só em `plataforma-infra` (ADR-0002); versão no catálogo central (`libs.versions.toml`, ADR-0012).
  - **Porta reutilizável** `ArmazenamentoDocumentos` em `plataforma-domain.documento` (`byte[] ler(URI)`, `URI armazenar(byte[],URI)`), dirigida por `URI` para casar com o seam `ReferenciaDocumento(URI)`. Adaptador `S3ArmazenamentoDocumentos` interpreta `s3://{bucket}/{chave}` (bucket = autoridade, chave = path).
  - **Cifragem em repouso server-side:** toda gravação aplica SSE — **SSE-KMS** (`aws:kms` + `ssekmsKeyId`) quando há chave gerenciada (governança auditável), senão **SSE-S3/AES256** (MinIO/dev). Para entes que exigem que o provedor de storage **nunca** veja texto claro, o caminho é cifragem **client-side/envelope** antes do `armazenar`, com a chave obtida pela porta `Cofre` — a interface não muda, só o adaptador.
  - **Isolamento multi-tenant por namespacing da URI:** a chave do objeto é prefixada pelo `ente` pelo produtor da URI (`s3://bucket/{ente}/...`), reforçada por política de bucket/IAM. O seam carrega só a URI (o `ente` vive no `DocumentoParaAssinar`, ADR-0015), então o adaptador confia na URI namespaced.
  - **Append-only:** o documento assinado é um novo objeto (URI derivada da origem: `…/x.pdf` → `…/x-assinado.pdf`); nunca sobrescreve a chave original. Correção = estorno + novo documento (docs/10).
  - **Binding** `@Configuration` condicional (`contabil.objectstore.enabled`), desligado por padrão; ligado quando a montagem de `ServicoAssinaturaGovBrAvancada` existir (RAZ-24). Expõe os dois seams como beans a partir do adaptador.
- **Consequências:** Base enxuta e backup transacional sem binário; capacidade reutilizável com a fronteira do ADR-0002/0014; portabilidade nuvem/on-prem e soberania via MinIO + envelope `Cofre`. Consistência documento↔fato é da aplicação (gravar objeto antes da tx; órfãos por GC). Testes com `S3Client` mockado (Testcontainers/MinIO fica para quando RAZ-26 destravar o sandbox).
- **Alternativas consideradas:** SDK proprietário de um provedor (rejeitado: preso a nuvem, sem on-prem/soberania); sistema de arquivos + NFS (rejeitado: sem cifragem/retenção/redundância nativas — já descartado no ADR-0009); cifragem só client-side como padrão (rejeitado como piso: SSE server-side é suficiente para a maioria dos entes e mais simples; envelope fica como opção soberana).

---

[← ADRs](./README.md)
