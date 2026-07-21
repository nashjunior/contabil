package br.contabil.plataforma.infra.iam;

import java.time.Instant;
import java.util.UUID;

record IdentidadeVerificada(
        String cpf,
        UUID enteId,
        String orgao,
        boolean mfaForteConcluido,
        Instant expiraEm) {}
