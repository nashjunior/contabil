-- Outbox de publicação/entrega garantida (ServicoEntrega) — fecha o gap de
-- RAZ-9 (motor de publicação) sem o qual nenhum bean de publicação (ex.:
-- PublicacaoTransparenciaExecucaoPort, execucao) consegue subir no contexto
-- Spring. Ref.: ADR-0004 (outbox idempotente), ADR-0011 (idempotência ponta a
-- ponta) — plataforma/plataforma-domain/entrega/ServicoEntrega.java.
--
-- Cobre só a ESCRITA (enqueue/status) — o worker de despacho assíncrono
-- (retentativa/DLQ) é o restante do motor de publicação, fora do escopo deste
-- fechamento pontual de wiring.

create table outbox_mensagem (
  id          uuid not null default uuid_generate_v4(),
  ente_id     uuid not null references ente(id),
  chave       text not null,
  destino     text not null,
  tipo        text not null,
  conteudo    text not null,
  status      text not null default 'enfileirado'
                check (status in ('enfileirado','duplicado','retentando','entregue','falha_permanente')),
  criado_em   timestamptz not null default clock_timestamp(),
  primary key (id),
  unique (ente_id, chave)                                -- idempotência do enqueue (ADR-0004/0011)
);
create index ix_outbox_mensagem_status on outbox_mensagem (status);

alter table outbox_mensagem enable row level security;
alter table outbox_mensagem force row level security;

create policy tenant_isolation on outbox_mensagem
  using      (ente_id = current_setting('app.ente_id', true)::uuid)
  with check (ente_id = current_setting('app.ente_id', true)::uuid);

revoke all on outbox_mensagem from public;
grant select, insert, update on outbox_mensagem to app_role;
