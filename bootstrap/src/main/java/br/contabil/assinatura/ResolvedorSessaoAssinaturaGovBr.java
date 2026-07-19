package br.contabil.assinatura;

import br.contabil.plataforma.domain.TenantId;
import jakarta.servlet.http.HttpSession;

interface ResolvedorSessaoAssinaturaGovBr {

    TenantId enteAutenticado(HttpSession sessao);
}
