-- Rollback manual de V9__execucao_pagamento_idempotencia.sql — RAZ-134.
--
-- Flyway Community não roda "undo migrations"; este script fica FORA de
-- db/migration e é aplicado manualmente (psql/CI) quando necessário. Uso
-- pretendido: ambiente efêmero/CI.

drop table if exists pagamento_idempotencia;
