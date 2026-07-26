# ADR-0033 · Frontend: composição (compound components + Context); proibido prop drilling em subcomponente aninhado

- **Status:** Aceita
- **Data:** 2026-07-26
- **Contexto:** Telas do F1 (Formulário — Conta PCASP, Gate de Aprovação 4-eyes, Tabela — Seleção em Lote, componentes-núcleo do design system RAZ-100 §2) são profundamente aninhadas: seção → campo → estado de validação/erro do servidor (`ErroContrato`). Sem regra explícita, o caminho de menor resistência é repassar prop por prop através de níveis intermediários que só a repassam — acopla componentes a dado que não usam e torna refatoração arriscada num sistema onde a tela é onde valores contábeis em trânsito aparecem antes de virar fato.
- **Decisão:** **Compound components + Context + hooks**, com regra explícita: **se um dado atravessa 3+ níveis de componente só para ser repassado — sem que os intermediários o usem — a solução é composição (`children`) ou Context, nunca uma prop "furando" um componente que não usa o valor.** Prop simples continua ok quando o dado não atravessa (é usado no próprio nível).

  Modelo de referência (`shared/ui/FormSection`): componente pai cria um Context com o estado do formulário (valores, erros vindos de `ErroContrato` do servidor); subcomponentes (`FormSection.Field`, `FormSection.Error`) leem via hook (`useFormSectionContext`), nunca por prop do pai. Critério prático:

  | Situação | Solução |
  |---|---|
  | Intermediário só repassa `children` sem olhar o dado | Composição — pai recebe o subcomponente pronto |
  | Subcomponentes irmãos/não-adjacentes precisam do mesmo estado (ex.: `Field` e `Error` do mesmo campo, ou `Badge Estágio`/`Badge Aprovação` do RAZ-100 §2 lendo o mesmo item de fila) | Context via compound component |
  | Dado só usado no próprio nível | Prop normal — a regra não proíbe prop, proíbe prop que atravessa 3+ níveis sem uso intermediário |

  Container/presentational: páginas de feature (`features/*/pages/`) são container (buscam dado via hook de `api/`, React Query — [ADR-0034](./0034-frontend-estado-client-api-tipado.md)); componentes em `components/`/`shared/ui/` são presentational, sem saber de fetch. Co-location: Context e os subcomponentes que o consomem vivem no mesmo arquivo/pasta — o consumidor externo só importa o compound completo, nunca o Context bruto.
- **Consequências:**
  - **+** Refatorar a forma de um dado interno não exige tocar componentes intermediários que nunca deveriam tê-lo conhecido.
  - **+** Testável por partes: subcomponente testado com um Provider de teste, sem montar a árvore inteira.
  - **−** Mais boilerplate inicial que prop simples — aceito porque o caso comum aqui (formulário/tabela 3+ níveis) paga o custo de prop drilling a médio prazo.
  - **−** "3+ níveis"/"olha ou só repassa" exige julgamento, não é 100% mecanizável em lint — fica em checklist de code review (guia do Bruno).
- **Alternativas consideradas:** Prop drilling aceito com limite de 2 níveis (rejeitada — a issue pede regra explícita, não um limite ambíguo; 2 já é o ponto onde intermediários começam a acoplar); um estado global único (Redux-like) para todo formulário (rejeitada — estado de formulário é local à árvore do compound component, não cross-feature; ver [ADR-0034](./0034-frontend-estado-client-api-tipado.md) sobre estado global mínimo).

---

[← ADRs](./README.md) · [ADR-0034](./0034-frontend-estado-client-api-tipado.md)
