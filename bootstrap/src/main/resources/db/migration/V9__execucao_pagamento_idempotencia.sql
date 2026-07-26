-- RAZ-134: Idempotency-Key ponta a ponta (ADR-0011) no registro de pagamento
-- (individual e lote, RAZ-105/ADR-0022) — reenviar a mesma requisição após
-- timeout/erro de rede não deve reprocessar o pagamento (nem duplicar o fato
-- contábil que ele lança no razão).
--
-- Mesmo padrão do outbox_mensagem (V4/ADR-0004): insert com
-- `on conflict (ente_id, chave) do nothing`; 0 linhas afetadas = a chave já
-- foi usada, devolve o pagamento_id já associado (replay idempotente) em vez
-- de repetir o efeito. A reserva acontece na MESMA transação do INSERT em
-- pagamento (RegistrarPagamento.executar, antes da escrituração) — se o caso
-- de uso falhar/for revertido depois da reserva (ex.: liquidação sem saldo),
-- a reserva também é revertida e a chave volta a ficar livre.
--
-- FK composta (trava 4b) para pagamento_id é `deferrable initially deferred`
-- de propósito: a reserva é inserida ANTES do insert em pagamento (mesmo id,
-- gerado antecipadamente em RegistrarPagamento), então a checagem da FK só
-- pode acontecer no commit da transação, quando as duas linhas já existem.

create table pagamento_idempotencia (
  id           uuid not null default uuid_generate_v4(),
  ente_id      uuid not null references ente(id),
  chave        text not null,
  pagamento_id uuid not null,
  criado_em    timestamptz not null default clock_timestamp(),
  primary key (id),
  unique (ente_id, chave),
  foreign key (ente_id, pagamento_id) references pagamento (ente_id, id)
    deferrable initially deferred
);

alter table pagamento_idempotencia enable row level security;
alter table pagamento_idempotencia force row level security;

create policy tenant_isolation on pagamento_idempotencia
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

revoke all on pagamento_idempotencia from public;
grant select, insert on pagamento_idempotencia to app_role;
