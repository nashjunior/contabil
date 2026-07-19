---
name: adr
description: Cria um novo ADR (Architecture Decision Record) versionado em docs/arquitetura-tecnica/adr/, no formato MADR enxuto, com o próximo número e Status, e atualiza o índice. Use quando o usuário tomar/registrar uma decisão de arquitetura ("registra um ADR", "/adr ...", "nova decisão de arquitetura").
---

# Novo ADR

Cria um Architecture Decision Record versionado, seguindo a prática já firmada em [docs/arquitetura-tecnica/adr/](../../../docs/arquitetura-tecnica/adr/README.md).

## Passos

1. **Descobrir o próximo número:** liste `docs/arquitetura-tecnica/adr/` e pegue o maior `NNNN`; o novo é `NNNN+1` com 4 dígitos.
2. **Definir o slug** a partir do título (kebab-case, sem acento): arquivo `NNNN-<slug>.md`.
3. **Criar o arquivo** com o template abaixo. **Data = data atual da sessão** (do contexto). **Status** = `Proposta` por padrão; use `Aceita` se o usuário disser que já está decidido.
4. **Atualizar o índice** [adr/README.md](../../../docs/arquitetura-tecnica/adr/README.md): adicionar uma linha na tabela (link, decisão, status).
5. Se este ADR **substitui** outro: novo ADR aponta "Supersede ADR-NNNN"; o antigo muda para Status `Substituída` com link para o sucessor (não se edita a decisão original — versiona-se).

## Template

```markdown
# ADR-NNNN · <Título da decisão>

- **Status:** Proposta
- **Data:** <AAAA-MM-DD>
- **Contexto:** <o problema/força que motiva a decisão>
- **Decisão:** <o que foi decidido, de forma afirmativa>
- **Consequências:** <o que resulta — bom e ruim; trade-offs>
- **Alternativas consideradas:** <opções descartadas e por quê>

---

[← ADRs](./README.md)
```

## Convenções (obrigatórias)

- H1 na primeira linha; rodapé `[← ADRs](./README.md)`.
- Tabelas com separador `| --- |`; Mermaid (se houver) com rótulos ASCII sem acento.
- Um ADR = uma decisão. Conciso. Racional e trade-off explícitos.
