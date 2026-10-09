package com.ondexia.pruebas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las fronteras de la arquitectura, comprobadas por el compilador de pruebas.
 *
 * <p>Un documento que dice «el dominio no conoce HTTP» describe una intención.
 * Esta clase la convierte en una condición que rompe el build — que es la
 * diferencia que importa dentro de un año.
 *
 * <p>Una parte ya la impone Maven: {@code ondexia-domain} no declara JPA ni
 * Spring, así que esas violaciones ni siquiera compilan. Lo que se comprueba
 * aquí es lo que vive dentro de {@code ondexia-api}, donde el muro es más
 * blando a propósito (doc 08 §1.1).
 *
 * <p>No arranca Spring: lee el bytecode. Corre en un segundo.
 */
class ArquitecturaTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ondexia");
    }

    @Test
    @DisplayName("El dominio no depende de nadie del proyecto")
    void elDominioEsIndependiente() {
        // Si esto falla, ondexia-facturacion ya no podría usar el dominio sin
        // arrastrar el núcleo comercial entero.
        noClasses()
                .that().resideInAPackage("com.ondexia.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.ondexia.application..", "com.ondexia.infrastructure..")
                .because("las dependencias apuntan hacia adentro (doc 08 §1)")
                .check(clases);
    }

    @Test
    @DisplayName("El dominio no conoce tecnología")
    void elDominioNoConoceTecnologia() {
        // Maven ya lo impide —el módulo no declara estas dependencias— pero la
        // regla queda escrita: si algún día alguien añadiera JPA al pom del
        // dominio «para una cosa rápida», esto lo detendría.
        noClasses()
                .that().resideInAPackage("com.ondexia.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.validation..",
                        "jakarta.servlet..",
                        "org.hibernate..",
                        "tools.jackson..",
                        "com.fasterxml.jackson..")
                .because("el dominio son reglas de negocio, sin tecnología (doc 08 §2.1)")
                .check(clases);
    }

    @Test
    @DisplayName("La aplicación no depende de la infraestructura")
    void laAplicacionNoDependeDeInfraestructura() {
        // Es la frontera que Maven NO impone, porque application e
        // infrastructure comparten módulo. Aquí es donde ArchUnit gana su
        // sueldo.
        //
        // Ya atrapó una: ConsultarContexto importaba EvaluadorPermisos, que es
        // el puente con Spring Security. Se resolvió subiendo la lógica a
        // PermisosEfectivos, en aplicación, y dejando en infraestructura solo el
        // bean que SpEL necesita.
        noClasses()
                .that().resideInAPackage("com.ondexia.application..")
                .should().dependOnClassesThat().resideInAPackage("com.ondexia.infrastructure..")
                .because("la aplicación orquesta el dominio; los adaptadores dependen de ella")
                .check(clases);
    }

    @Test
    @DisplayName("La aplicación no conoce HTTP")
    void laAplicacionNoConoceHttp() {
        noClasses()
                .that().resideInAPackage("com.ondexia.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.http..",
                        "jakarta.servlet..")
                .because("un caso de uso vale igual llamado desde una cola que desde un endpoint")
                .check(clases);
    }

    @Test
    @DisplayName("El API Core no toca firma digital ni SUNAT")
    void elNucleoComercialNoFirmaNiHablaConSunat() {
        // El boundary de DT-13. Hoy ninguna de estas bibliotecas está en el
        // classpath, así que la regla no puede fallar. Ese es el objetivo: falla
        // el día que alguien añada la dependencia, que es cuando hay que
        // detenerse a pensar.
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.apache.xml.security..",
                        "javax.xml.crypto..",
                        "jakarta.xml.soap..",
                        "javax.xml.soap..")
                .because("""
                        el API Core nunca abre una conexión hacia SUNAT ni maneja \
                        certificados: publica en SQS y responde (DTE §3.3, DT-13)""")
                .check(clases);
    }

    @Test
    @DisplayName("Los controladores viven en infrastructure/entrada/web")
    void losControladoresEstanEnSuSitio() {
        classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("com.ondexia.infrastructure.entrada.web..")
                .because("un controlador es un adaptador de entrada (doc 08 §2.2)")
                .check(clases);
    }

    @Test
    @DisplayName("Las entidades JPA viven en persistencia y llevan el sufijo Jpa")
    void lasEntidadesJpaEstanEnSuSitio() {
        // El sufijo no es decorativo: evita confundir el agregado Empresa con la
        // fila EmpresaJpa, que es el error más fácil de cometer con este diseño.
        classes()
                .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().resideInAPackage("com.ondexia.infrastructure.salida.persistencia..")
                .andShould().haveSimpleNameEndingWith("Jpa")
                .check(clases);
    }

    @Test
    @DisplayName("Sin inyección por campo")
    void sinInyeccionPorCampo() {
        // Un campo inyectado no se puede pasar en un constructor, así que la
        // clase deja de ser instanciable sin levantar Spring. Y oculta cuántas
        // dependencias tiene: un constructor con ocho parámetros incomoda y
        // avisa; ocho campos anotados, no.
        fields()
                .should().notBeAnnotatedWith(
                        org.springframework.beans.factory.annotation.Autowired.class)
                .because("las dependencias van por constructor, que es lo que las hace visibles")
                .check(clases);
    }

    @Test
    @DisplayName("Nadie usa java.util.Date")
    void solamenteFechasModernas() {
        // Mutable y de zona ambigua. En un sistema donde la fecha de emisión
        // tiene efecto tributario, eso es un defecto esperando ocurrir.
        noClasses()
                .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
                .because("se usa java.time")
                .check(clases);
    }
}
