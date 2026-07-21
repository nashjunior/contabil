package br.contabil.plataforma.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    private static final TenantId ENTE_A = TenantId.de(UUID.randomUUID().toString());
    private static final TenantId ENTE_B = TenantId.de(UUID.randomUUID().toString());

    @Test
    void semEscopoAtivoAtualEhVazioEExigirAtualEstouraFailClosed() {
        assertThat(TenantContext.atual()).isEmpty();
        assertThatIllegalStateException()
                .isThrownBy(TenantContext::exigirAtual)
                .withMessageContaining("RAZ-23");
    }

    @Test
    void ativarParaPopulaEFecharLimpa() throws Exception {
        AutoCloseable escopo = TenantContext.ativarPara(ENTE_A);
        try (escopo) {
            assertThat(TenantContext.atual()).contains(ENTE_A);
            assertThat(TenantContext.exigirAtual()).isEqualTo(ENTE_A);
        }

        assertThat(TenantContext.atual()).isEmpty();
    }

    @Test
    void escopoAninhadoRestauraOAnteriorAoFecharSemVazarNemApagarOExterno() throws Exception {
        AutoCloseable externo = TenantContext.ativarPara(ENTE_A);
        try (externo) {
            AutoCloseable interno = TenantContext.ativarPara(ENTE_B);
            try (interno) {
                assertThat(TenantContext.atual()).contains(ENTE_B);
            }
            assertThat(TenantContext.atual())
                    .as("fechar o escopo interno restaura o tenant do escopo externo, não limpa tudo")
                    .contains(ENTE_A);
        }
        assertThat(TenantContext.atual()).isEmpty();
    }

    @Test
    void naoAceitaTenantIdNulo() {
        assertThatNullPointerException().isThrownBy(() -> TenantContext.ativarPara(null));
    }

    @Test
    void ehIsoladoPorThreadNaoVazaParaOutraThreadEmExecucaoConcorrente() throws Exception {
        AutoCloseable escopo = TenantContext.ativarPara(ENTE_A);
        try (escopo) {
            AtomicReference<Optional<TenantId>> vistoNaOutraThread = new AtomicReference<>();
            CountDownLatch pronto = new CountDownLatch(1);
            Thread outra = new Thread(() -> {
                vistoNaOutraThread.set(TenantContext.atual());
                pronto.countDown();
            });
            outra.start();
            pronto.await();
            outra.join();

            assertThat(vistoNaOutraThread.get()).isEmpty();
            assertThat(TenantContext.atual()).contains(ENTE_A);
        }
    }
}
