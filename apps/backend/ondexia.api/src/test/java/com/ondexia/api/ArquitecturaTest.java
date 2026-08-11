package com.ondexia.api;

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
 * <p>Un documento que dice «el API Core nunca llama a SUNAT» describe una
 * intención. Esta clase la convierte en una condición que rompe el build. La
 * diferencia importa dentro de un año, cuando el documento lo lea alguien con
 * prisa — o nadie.
 *
 * <p>No arranca Spring: lee el bytecode compilado. Corre en un segundo.
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
    @DisplayName("El dominio no depende de la API")
    void elDominioNoDependeDeLaApi() {
        // Si esto falla, la separación en dos módulos Maven dejó de significar
        // nada: ondexia-facturacion ya no podría usar el dominio sin arrastrar
        // el núcleo comercial entero.
        noClasses()
                .that().resideInAPackage("com.ondexia.domain..")
                .should().dependOnClassesThat().resideInAPackage("com.ondexia.api..")
                .because("""
                        el dominio se comparte con ondexia-facturacion; si importa de la API, \
                        la separación en modulos se perdió (doc 03 §4)""")
                .check(clases);
    }

    @Test
    @DisplayName("El dominio no conoce HTTP ni seguridad web")
    void elDominioNoConoceHttp() {
        // Es lo único que se conserva de hexagonal estricta, y lo que rinde:
        // las dependencias apuntan hacia adentro.
        noClasses()
                .that().resideInAPackage("com.ondexia.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.http..",
                        "org.springframework.security..",
                        "jakarta.servlet..")
                .because("el dominio no sabe que existe HTTP (doc 03 §4.1)")
                .check(clases);
    }

    @Test
    @DisplayName("El API Core no toca firma digital ni SUNAT")
    void elNucleoComercialNoFirmaNiHablaConSunat() {
        // El boundary de DT-13. El núcleo publica en una cola y responde; toda
        // comunicación fiscal pasa por ondexia-facturacion, que es el único
        // componente que toca certificados.
        //
        // Hoy ninguna de estas bibliotecas está en el classpath, así que la
        // regla no puede fallar. Ese es el objetivo: falla el día que alguien
        // añada la dependencia, que es cuando hay que detenerse a pensar.
        noClasses()
                .that().resideInAPackage("com.ondexia.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.apache.xml.security..",
                        "javax.xml.crypto..",
                        "jakarta.xml.soap..",
                        "javax.xml.soap..",
                        "java.security.cert..")
                .because("""
                        el API Core nunca abre una conexión hacia SUNAT ni maneja \
                        certificados: publica en SQS y responde (DTE §3.3, DT-13)""")
                .check(clases);
    }

    @Test
    @DisplayName("Los controladores no usan repositorios directamente")
    void losControladoresPasanPorLaCapaDeAplicacion() {
        // Un controlador que consulta el repositorio se salta la capa donde
        // viven las reglas y la auditoría. Funciona, y es como se erosiona la
        // arquitectura: primero una consulta «simple», y a los seis meses hay
        // lógica de negocio repartida entre la web y los servicios.
        noClasses()
                .that().resideInAPackage("..web..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .because("la capa web pasa por aplicacion, que es donde vive la auditoría")
                .check(clases);
    }

    @Test
    @DisplayName("Los controladores viven en un paquete web")
    void losControladoresEstanEnSuSitio() {
        classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..web..")
                .because("el corte es por dominio, y dentro de cada uno por capa (doc 03 §4.1)")
                .check(clases);
    }

    @Test
    @DisplayName("Sin inyección por campo")
    void sinInyeccionPorCampo() {
        // Un campo inyectado no se puede pasar en un constructor, así que la
        // clase deja de ser instanciable en una prueba sin levantar Spring. Y
        // oculta cuántas dependencias tiene: un constructor con ocho parámetros
        // incomoda y avisa; ocho campos anotados, no.
        fields()
                .should().notBeAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
                .because("las dependencias van por constructor, que es lo que las hace visibles")
                .check(clases);
    }

    @Test
    @DisplayName("Nadie usa java.util.Date ni Calendar")
    void solamenteFechasModernas() {
        // Ambas son mutables y de zona ambigua. En un sistema donde la fecha de
        // emisión tiene efecto tributario, una fecha que cambia sola o que se
        // interpreta en la zona del servidor es un defecto esperando ocurrir.
        noClasses()
                .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
                .because("se usa java.time; Date es mutable y de zona ambigua")
                .check(clases);
    }
}
