package br.contabil.consulta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.application.ConsultarContas;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.ContaResumo;
import br.contabil.razao.domain.PaginaContas;

/**
 * Testa só a adaptação HTTP → use case (RAZ-117): o RBAC/tenant/clamp já é coberto por
 * {@code ConsultarContasTest} — aqui o use case é mock, e o foco é o mapeamento de
 * path/query para {@code executar(..)} e a serialização do {@code {itens, proximoCursor}}
 * (lista §6.1). Sem dinheiro no catálogo ⇒ {@code ObjectMapper} padrão basta.
 */
@ExtendWith(MockitoExtension.class)
class CatalogoContasControllerTest {

    @Mock
    private ConsultarContas consultarContas;

    private final ObjectMapper json = new ObjectMapper();

    private CatalogoContasController controller() {
        return new CatalogoContasController(consultarContas);
    }

    private Sessao sessaoDe(TenantId ente) {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), ente, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void contasAdaptaQueryEMapeiaItensComProximoCursor() throws Exception {
        UUID enteId = UUID.randomUUID();
        UUID contaId = UUID.randomUUID();
        UUID contaPaiId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        PaginaContas pagina = new PaginaContas(
                List.of(new ContaResumo(
                        new ContaContabilId(contaId), "1.1.1", "Caixa e bancos", "D", "patrimonial", true,
                        new ContaContabilId(contaPaiId))),
                Optional.of("prox123"));
        when(consultarContas.executar(
                        eq(sessao), eq(new TenantId(enteId)), eq(Optional.of("caixa")), eq(Optional.of(50)), eq(Optional.empty())))
                .thenReturn(pagina);

        CatalogoContasController.ContasResponse resposta = controller().contas(enteId, "caixa", null, 50, sessao);

        assertThat(resposta.proximoCursor()).isEqualTo("prox123");
        assertThat(resposta.itens()).hasSize(1);
        CatalogoContasController.ContaResumoResponse item = resposta.itens().get(0);
        assertThat(item.id()).isEqualTo(contaId);
        assertThat(item.codigo()).isEqualTo("1.1.1");
        assertThat(item.naturezaSaldo()).isEqualTo("D");
        assertThat(item.naturezaInformacao()).isEqualTo("patrimonial");
        assertThat(item.escrituravel()).isTrue();
        assertThat(item.contaPaiId()).isEqualTo(contaPaiId);
        // Lista §6.1: envelope {itens, proximoCursor}.
        String corpo = json.writeValueAsString(resposta);
        assertThat(corpo).contains("\"itens\":[").contains("\"proximoCursor\":\"prox123\"");
    }

    @Test
    void contaRaizSerializaContaPaiIdNulo() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        PaginaContas pagina = new PaginaContas(
                List.of(new ContaResumo(
                        ContaContabilId.novo(), "1", "Ativo", "D", "patrimonial", false, null)),
                Optional.empty());
        when(consultarContas.executar(
                        eq(sessao), eq(new TenantId(enteId)), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty())))
                .thenReturn(pagina);

        CatalogoContasController.ContasResponse resposta = controller().contas(enteId, null, null, null, sessao);

        assertThat(resposta.proximoCursor()).isNull();
        assertThat(resposta.itens().get(0).contaPaiId()).isNull();
    }

    @Test
    void contasPropagaErroDeNegocioSemMapearNoController() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));
        when(consultarContas.executar(
                        eq(sessao), eq(new TenantId(enteId)), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty())))
                .thenThrow(new SemPermissaoException("sem_permissao"));

        assertThatThrownBy(() -> controller().contas(enteId, null, null, null, sessao))
                .isInstanceOf(SemPermissaoException.class);
    }
}
