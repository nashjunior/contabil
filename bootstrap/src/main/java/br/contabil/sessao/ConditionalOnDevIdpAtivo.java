package br.contabil.sessao;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Fonte única do gate de duas flags do IdP de dev local (RAZ-228/ADR-0052 item 1) — {@link
 * SessaoDevIdpConfiguration} (bean do assinador) e {@link SessaoDevIdpController} (rota HTTP)
 * precisam ficar SEMPRE em sincronia: uma sem a outra deixaria o bean existir sem rota (inócuo)
 * ou, pior, a rota existir sem o bean (erro de wiring). Meta-anotação em vez de repetir o array
 * de nomes de propriedade nas duas classes — mudar a condição no futuro (ex.: uma terceira flag)
 * é uma mudança num só lugar.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(name = {"siafic.dev-idp.enabled", "siafic.iam.enabled"}, havingValue = "true")
@interface ConditionalOnDevIdpAtivo {}
