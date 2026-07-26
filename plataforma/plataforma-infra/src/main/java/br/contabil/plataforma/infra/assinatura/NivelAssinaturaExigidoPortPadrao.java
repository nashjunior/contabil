package br.contabil.plataforma.infra.assinatura;

import org.springframework.stereotype.Component;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.assinatura.NivelAssinaturaExigidoPort;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelAssinatura;

/**
 * F0: sempre {@code AVANCADA_GOVBR} — único nível suportado pelo provedor
 * (ADR-0027 (e); transversais/01-assinatura-eletronica.md §Faseamento). Sem
 * parametrização por ente/tipo ainda; troca por um adaptador com tabela de
 * administração é issue própria, sem mudar este port.
 */
@Component
public class NivelAssinaturaExigidoPortPadrao implements NivelAssinaturaExigidoPort {

    @Override
    public NivelAssinatura nivelExigido(TenantId ente, String tipoDocumento) {
        return NivelAssinatura.AVANCADA_GOVBR;
    }
}
