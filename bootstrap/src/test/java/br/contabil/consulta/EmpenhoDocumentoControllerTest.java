package br.contabil.consulta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import br.contabil.execucao.application.ConsultarDocumentoEmpenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class EmpenhoDocumentoControllerTest {

    @Mock
    private ObjectProvider<ConsultarDocumentoEmpenho> consultarDocumentoEmpenhoProvider;

    @Mock
    private ConsultarDocumentoEmpenho consultarDocumentoEmpenho;

    private EmpenhoDocumentoController controller() {
        return new EmpenhoDocumentoController(consultarDocumentoEmpenhoProvider);
    }

    private Sessao sessaoDe(TenantId ente) {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), ente, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void devolveOPdfComContentTypeApplicationPdf() {
        UUID enteId = UUID.randomUUID();
        UUID empenhoId = UUID.randomUUID();
        byte[] pdf = "conteudo-pdf".getBytes();
        Sessao sessao = sessaoDe(new TenantId(enteId));

        when(consultarDocumentoEmpenhoProvider.getIfAvailable()).thenReturn(consultarDocumentoEmpenho);
        when(consultarDocumentoEmpenho.executar(eq(sessao), eq(new TenantId(enteId)), eq(new EmpenhoId(empenhoId))))
                .thenReturn(pdf);

        ResponseEntity<byte[]> resposta = controller().documento(enteId, empenhoId, sessao);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(resposta.getBody()).isSameAs(pdf);
    }

    @Test
    void semObjectStoreConfiguradoEstouraEmVezDeExpor500Silencioso() {
        UUID enteId = UUID.randomUUID();
        UUID empenhoId = UUID.randomUUID();
        Sessao sessao = sessaoDe(new TenantId(enteId));

        when(consultarDocumentoEmpenhoProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> controller().documento(enteId, empenhoId, sessao))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contabil.objectstore.enabled");
    }
}
