package br.contabil.configuracao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.HttpSessionMutexListener;

@Configuration(proxyBeanMethods = false)
class SessaoWebConfiguration {

    /**
     * Publica um mutex dedicado por sessao HTTP (atributo criado na criacao da sessao). Com ele,
     * {@code WebUtils.getSessionMutex(sessao)} devolve sempre o mesmo objeto estavel para uma dada
     * sessao, em vez do facade da sessao gerido pelo container — cuja identidade nao e garantida
     * entre requisicoes concorrentes. E o que torna os blocos {@code synchronized} do fluxo de
     * assinatura gov.br realmente mutuamente exclusivos.
     */
    @Bean
    HttpSessionMutexListener httpSessionMutexListener() {
        return new HttpSessionMutexListener();
    }
}
