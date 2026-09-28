package co.tcc.eventos.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Las reglas de la arquitectura hexagonal, verificadas en cada compilación en vez de confiar en la disciplina.
 * Los módulos Maven ya impiden la mayoría de las dependencias prohibidas (el dominio no tiene Spring en su
 * classpath); estas reglas cubren lo que Maven no ve: paquetes dentro de un módulo y dependencias transitivas.
 */
@AnalyzeClasses(packages = "co.tcc.eventos", importOptions = ImportOption.DoNotIncludeTests.class)
class DependenciasPruebas {

    private static final String DOMINIO = "co.tcc.eventos.dominio..";
    private static final String CONTRATOS = "co.tcc.eventos.contratos..";
    private static final String APLICACION = "co.tcc.eventos.aplicacion..";
    private static final String INFRAESTRUCTURA = "co.tcc.eventos.infraestructura..";
    private static final String API = "co.tcc.eventos.api..";
    private static final String PROCESADOR = "co.tcc.eventos.procesador..";
    private static final String NOTIFICADOR = "co.tcc.eventos.notificador..";

    @ArchTest
    static final ArchRule el_dominio_no_depende_de_ningun_otro_proyecto_ni_libreria = classes()
            .that().resideInAPackage(DOMINIO)
            .should().onlyDependOnClassesThat().resideInAnyPackage(DOMINIO, "java..")
            .because("el dominio es Java puro: las reglas del negocio no conocen ninguna tecnología");

    @ArchTest
    static final ArchRule los_contratos_no_dependen_de_nada = classes()
            .that().resideInAPackage(CONTRATOS)
            .should().onlyDependOnClassesThat().resideInAnyPackage(CONTRATOS, "java..");

    @ArchTest
    static final ArchRule la_aplicacion_solo_depende_del_dominio_y_de_la_abstraccion_de_logs = classes()
            .that().resideInAPackage(APLICACION)
            .should().onlyDependOnClassesThat().resideInAnyPackage(APLICACION, DOMINIO, "java..", "org.slf4j..");

    @ArchTest
    static final ArchRule el_nucleo_no_conoce_spring = noClasses()
            .that().resideInAnyPackage(DOMINIO, APLICACION, CONTRATOS)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "tools.jackson..");

    @ArchTest
    static final ArchRule capas = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Dominio").definedBy(DOMINIO)
            .layer("Contratos").definedBy(CONTRATOS)
            .layer("Aplicacion").definedBy(APLICACION)
            .layer("Infraestructura").definedBy(INFRAESTRUCTURA)
            .layer("Api").definedBy(API)
            .layer("Procesador").definedBy(PROCESADOR)
            .layer("Notificador").definedBy(NOTIFICADOR)
            // Los hosts son raíces de composición: nadie depende de ellos y no dependen entre sí.
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Procesador").mayNotBeAccessedByAnyLayer()
            .whereLayer("Notificador").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infraestructura").mayOnlyBeAccessedByLayers("Api", "Procesador", "Notificador")
            .whereLayer("Aplicacion").mayOnlyBeAccessedByLayers("Infraestructura", "Api", "Procesador", "Notificador")
            .whereLayer("Contratos").mayOnlyBeAccessedByLayers("Infraestructura", "Api", "Procesador", "Notificador");

    @ArchTest
    static final ArchRule el_dominio_no_tiene_setters_publicos = methods()
            .that().areDeclaredInClassesThat().resideInAPackage(DOMINIO)
            .and().arePublic()
            .should().haveNameNotMatching("set[A-Z].*")
            .because("los invariantes solo cambian por métodos del dominio (p. ej. Guia.aplicar)");

    @ArchTest
    static final ArchRule el_dominio_no_expone_campos_mutables = fields()
            .that().areDeclaredInClassesThat().resideInAPackage(DOMINIO)
            .and().arePublic()
            .should().beFinal();

    @ArchTest
    static final ArchRule los_casos_de_uso_no_llevan_anotaciones_de_framework = classes()
            .that().resideInAPackage("co.tcc.eventos.aplicacion.casosuso..")
            .should().notBeAnnotatedWith("org.springframework.stereotype.Service")
            .andShould().notBeAnnotatedWith("org.springframework.stereotype.Component")
            .because("los casos de uso son clases normales: se crean con @Bean en cada raíz de composición");

    @ArchTest
    static final ArchRule solo_la_infraestructura_habla_con_kafka_directamente = noClasses()
            .that().resideInAnyPackage(API, PROCESADOR, NOTIFICADOR)
            .should().dependOnClassesThat().haveFullyQualifiedName("org.apache.kafka.clients.producer.KafkaProducer")
            .because("todo lo que publica pasa por ProductorKafka: nadie publica con garantías distintas por accidente");
}
