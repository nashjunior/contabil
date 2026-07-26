package br.contabil.plataforma.domain.assinatura;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelAssinatura;

/**
 * Parametrização do nível de assinatura exigido por ente × tipo de documento
 * (transversais/01-assinatura-eletronica.md item 4; ADR-0027 (e)). Este port
 * decide só a forma: {@code AVANCADA_GOVBR} é o default quando o ente não tem
 * parametrização própria (único nível suportado em F0) — a UI/tabela de
 * administração é issue própria, fora deste escopo.
 */
public interface NivelAssinaturaExigidoPort {

    NivelAssinatura nivelExigido(TenantId ente, String tipoDocumento);
}
