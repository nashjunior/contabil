package br.contabil.plataforma.domain.dinheiro;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um campo, parâmetro ou retorno que representa dinheiro/valor contábil.
 *
 * <p>Invariante do sistema (ADR-0006, arquitetura-tecnica §8): dinheiro é SEMPRE decimal exato.
 * Elementos anotados com {@code @Monetario} DEVEM ser {@link java.math.BigDecimal} ou
 * {@link br.contabil.plataforma.domain.Dinheiro}. É proibido {@code float}/{@code double}/
 * {@code Float}/{@code Double}.
 *
 * <p>Ponto de ancoragem das travas de build: Error Prone (compile-time) e ArchUnit
 * {@code br.contabil.arquitetura.GuardrailsArquiteturaTest} (estrutural).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE})
public @interface Monetario {
}
