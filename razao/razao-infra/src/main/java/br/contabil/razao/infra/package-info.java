/**
 * Adaptadores de persistência do razão (JdbcTemplate — controle explícito do
 * batch insert dos lançamentos e da função de numeração; o modelo append-only
 * não precisa de gerenciamento de entidade JPA). Implementa as portas
 * definidas em {@code razao-application}.
 */
package br.contabil.razao.infra;
