package br.contabil.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Guardrails estruturais que FALHAM o build (arquitetura-tecnica §8).
 *
 * <p>Executa sobre TODO o bytecode de produção do monólito modular (ADR-0002). Rodado por
 * {@code ./gradlew check}. Uma violação = build vermelho, sem exceção configurável no código.
 *
 * <p>Cobre 3 das 5 travas da RAZ-2:
 * <ol>
 *   <li>ADR-0006 — dinheiro é decimal; proibido float/double;</li>
 *   <li>fronteiras de camada domain/application/infra (ADR-0002);</li>
 *   <li>repositório do razão append-only (sem update/delete).</li>
 * </ol>
 * As outras duas (vazamento cross-tenant/RLS e gitleaks) vivem, respectivamente, em {@code
 * bootstrap/src/test/java/br/contabil/migration/VazamentoCrossTenantRlsTest.java} (Postgres
 * real via Testcontainers, roda em {@code ./gradlew check}) e em {@code .gitleaks.toml} +
 * {@code .github/workflows/ci.yml} (RAZ-16, bloqueante — ADR-0003).
 */
// Não usar DoNotIncludeJars: os módulos de produção chegam como JAR via project(...),
// e o filtro packages="br.contabil" já restringe a análise ao nosso código.
@AnalyzeClasses(
    packages = "br.contabil",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class GuardrailsArquiteturaTest {

  private static final String PKG_DOMAIN = "..domain..";
  private static final String PKG_APPLICATION = "..application..";
  private static final String PKG_INFRA = "..infra..";

  // ---------------------------------------------------------------------------
  // TRAVA 1 — ADR-0006: dinheiro é decimal exato; nada de ponto flutuante binário.
  // ---------------------------------------------------------------------------

  /** Nenhum campo de produção pode ter tipo de ponto flutuante binário. */
  @ArchTest
  static final ArchRule nenhum_campo_usa_ponto_flutuante =
      noFields()
          .should()
          .haveRawType(double.class)
          .orShould()
          .haveRawType(float.class)
          .orShould()
          .haveRawType(Double.class)
          .orShould()
          .haveRawType(Float.class)
          .because(
              "ADR-0006: valores monetários e contábeis são decimais exatos (BigDecimal/Dinheiro); "
                  + "float/double introduzem erro de arredondamento e violam a partida dobrada (Σ=Σ)");

  /** Campos explicitamente monetários devem ser BigDecimal ou Dinheiro. */
  @ArchTest
  static final ArchRule campo_monetario_e_decimal =
      fields()
          .that()
          .areAnnotatedWith("br.contabil.plataforma.domain.dinheiro.Monetario")
          .should()
          .haveRawType("java.math.BigDecimal")
          .orShould()
          .haveRawType("br.contabil.plataforma.domain.Dinheiro")
          .because("ADR-0006: elemento @Monetario é sempre decimal exato");

  /** Métodos não podem retornar float/double (evita vazar aritmética binária de dinheiro). */
  @ArchTest
  static final ArchRule metodo_nao_retorna_ponto_flutuante =
      noMethods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAnyPackage(PKG_DOMAIN, PKG_APPLICATION)
          .should()
          .haveRawReturnType(double.class)
          .orShould()
          .haveRawReturnType(float.class)
          .orShould()
          .haveRawReturnType(Double.class)
          .orShould()
          .haveRawReturnType(Float.class)
          .because("ADR-0006: domínio/aplicação não expõem dinheiro em ponto flutuante");

  // ---------------------------------------------------------------------------
  // TRAVA 2 — Fronteiras de camada (ADR-0002, monólito modular).
  // ---------------------------------------------------------------------------

  /** Camadas: domínio no núcleo, aplicação orquestra, infra nas bordas. */
  @ArchTest
  static final ArchRule camadas_respeitadas =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .withOptionalLayers(true) // scaffold: camadas ainda vazias são permitidas (populam ao longo das RAZ-*)
          .layer("Domain")
          .definedBy(PKG_DOMAIN)
          .layer("Application")
          .definedBy(PKG_APPLICATION)
          .layer("Infra")
          .definedBy(PKG_INFRA)
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Application", "Infra")
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Infra")
          .whereLayer("Infra")
          .mayNotBeAccessedByAnyLayer()
          .because("ADR-0002: dependências apontam para dentro; infra é detalhe substituível");

  /** O domínio é POJO puro: sem Spring, sem JPA, sem detalhes de frameworks. */
  @ArchTest
  static final ArchRule dominio_nao_depende_de_framework =
      noClasses()
          .that()
          .resideInAPackage(PKG_DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "javax.persistence..",
              "org.hibernate..",
              "com.fasterxml.jackson..")
          .because("ADR-0002: o domínio contábil não conhece framework, ORM nem serialização");

  /**
   * A aplicação também é POJO: os use cases não importam framework (ADR-0002 / AGENTS.md /
   * guardiao-arquitetura). Nada de {@code @Service}/{@code @Transactional}/{@code @Bean} no
   * use case — o wiring e a transação (advisor na borda) são responsabilidade da infra.
   */
  @ArchTest
  static final ArchRule aplicacao_nao_depende_de_framework =
      noClasses()
          .that()
          .resideInAPackage(PKG_APPLICATION)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "javax.persistence..",
              "jakarta.transaction..",
              "javax.transaction..",
              "org.hibernate..",
              "com.fasterxml.jackson..")
          .because(
              "ADR-0002/AGENTS.md: os use cases são POJO; a infra faz o wiring (@Bean) e detém a "
                  + "transação (advisor na borda) — a application não anota nem importa framework");

  /**
   * RAZ-21: todo {@code executar(..)} da application declara {@link
   * br.contabil.plataforma.domain.TenantId} como parâmetro. O advisor AOP em
   * {@code bootstrap/TenantContextUseCasesConfiguration} extrai o {@code TenantId} desse
   * argumento para setar {@code app.ente_id} (RLS forçada) antes de chamar o use case — sem
   * esse parâmetro, o advisor falha com {@code IllegalStateException} só em RUNTIME. Esta
   * regra torna a exigência estática (falha no build, não na primeira chamada em produção).
   */
  @ArchTest
  static final ArchRule executar_da_application_declara_tenant_id =
      methods()
          .that()
          .haveName("executar")
          .and()
          .areDeclaredInClassesThat()
          .resideInAPackage(PKG_APPLICATION)
          .should(declararTenantIdComoParametro())
          .because(
              "RAZ-21/ADR-0003: o advisor que seta app.ente_id (RLS forçada) deriva o tenant do "
                  + "primeiro argumento TenantId de executar(..) — sem ele, RLS deny-by-default "
                  + "nega tudo em runtime");

  private static ArchCondition<JavaMethod> declararTenantIdComoParametro() {
    return new ArchCondition<>("declarar um parâmetro do tipo TenantId") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        boolean temTenantId = method.getRawParameterTypes().stream()
            .anyMatch(tipo -> tipo.getFullName().equals("br.contabil.plataforma.domain.TenantId"));
        String mensagem = method.getFullName()
            + (temTenantId ? " declara TenantId como parâmetro" : " NÃO declara TenantId como parâmetro");
        events.add(new SimpleConditionEvent(method, temTenantId, mensagem));
      }
    };
  }

  // ---------------------------------------------------------------------------
  // TRAVA 3 — Repositório do razão é append-only (sem update/delete).
  // ---------------------------------------------------------------------------

  /** Repositórios do razão não declaram operações de mutação/remoção. */
  @ArchTest
  static final ArchRule repositorio_do_razao_e_append_only =
      noMethods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("..razao..")
          .and()
          .areDeclaredInClassesThat()
          .haveSimpleNameEndingWith("Repository")
          .should()
          .haveNameMatching("(?i)(update|delete|remove|edit|patch|truncate|drop|overwrite|replace).*")
          .because(
              "razão é imutável/append-only (arquitetura-tecnica §8): lançamentos só são estornados "
                  + "por novo lançamento, nunca alterados ou removidos");

  /** Persistência do razão não usa @Modifying (JPA UPDATE/DELETE). */
  @ArchTest
  static final ArchRule razao_nao_usa_modifying =
      noMethods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("..razao..infra..")
          .should()
          .beAnnotatedWith("org.springframework.data.jpa.repository.Modifying")
          .because("append-only: nenhuma query de modificação no razão");

  /** Ninguém no razão chama EntityManager.remove(...). */
  @ArchTest
  static final ArchRule razao_nao_chama_entitymanager_remove =
      noClasses()
          .that()
          .resideInAPackage("..razao..")
          .should()
          .callMethod("jakarta.persistence.EntityManager", "remove", "java.lang.Object")
          .because("append-only: proibida a remoção física de registros do razão");

  // ---------------------------------------------------------------------------
  // TRAVA 4 (RAZ-17) — isolamento cross-módulo: execução/razão/plataforma são
  // módulos-irmãos do monólito modular (ADR-0002) — só compartilham o shared
  // kernel (plataforma), nunca dependem um do outro diretamente. slices()
  // trava isso no bytecode real (defesa em profundidade além do que
  // build.gradle.kts já impede em tempo de compilação por módulo).
  // ---------------------------------------------------------------------------

  /**
   * Módulos de negócio (execução, razão, plataforma) não formam ciclo de dependência.
   * Hoje só razão tem classes de domínio (execução ainda é só package-info) — a regra já
   * protege a fronteira antes de execução ganhar as suas primeiras classes, em vez de
   * nascer com uma violação já acomodada.
   */
  @ArchTest
  static final ArchRule modulos_de_negocio_sao_livres_de_ciclo =
      slices()
          .matching("br.contabil.(*)..")
          .should()
          .beFreeOfCycles()
          .because(
              "ADR-0002: monólito modular com fronteiras internas explícitas (execução, razão, "
                  + "plataforma) — módulos podem depender do shared kernel, mas dependência circular "
                  + "entre módulos de negócio quebra a fronteira que permite extração futura");

  // Guard extra: garante que o import realmente analisou classes (evita "regra que passa vazia").
  // Idioma ArchUnit: método @ArchTest recebendo JavaClasses.
  @ArchTest
  static void ha_classes_de_producao_para_analisar(JavaClasses classes) {
    if (classes.isEmpty()) {
      throw new AssertionError(
          "Nenhuma classe de produção importada — os guardrails estariam passando por vacuidade. "
              + "Confirme que architecture-tests depende dos módulos de produção (execucao/razao/plataforma).");
    }
  }
}
