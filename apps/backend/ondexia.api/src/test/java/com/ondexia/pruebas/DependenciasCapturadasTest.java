package com.ondexia.pruebas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class DependenciasCapturadasTest {

    @Test
    void resuelveElTipoCapturadoComoAccesoTecnologico() {
        ArchConfiguration.withThreadLocalScope(configuracion -> {
            // Aísla la resolución por accesos; otras rutas ocultarían el defecto.
            for (String categoria : new String[] {"MemberTypes", "Supertypes", "EnclosingTypes",
                    "AnnotationTypes", "GenericSignatureTypes"}) {
                configuracion.setProperty("import.dependencyResolutionProcess.maxIterationsFor"
                        + categoria, "0");
            }
            configuracion.setProperty(
                    "import.dependencyResolutionProcess.maxIterationsForAccessesToTypes", "1");
            var clase = new ClassFileImporter().importClass(CapturaTecnologica.class);
            var capturada = clase.getTryCatchBlocks().iterator().next()
                    .getCaughtThrowables().iterator().next();

            assertThat(capturada.getName()).isEqualTo("java.sql.SQLException");
            assertThat(capturada.isFullyImported()).isTrue();
        });
    }

    @Test
    void unaExcepcionCapturadaEsDependenciaTecnologica() {
        var clases = new ClassFileImporter().importClasses(CapturaTecnologica.class);
        var dependencias = clases.get(CapturaTecnologica.class).getDirectDependenciesFromSelf();

        assertThat(dependencias)
                .anyMatch(dependencia -> dependencia.getTargetClass().getName()
                        .equals("java.sql.SQLException"));
        assertThat(noClasses().should().dependOnClassesThat().resideInAPackage("java.sql..")
                .evaluate(clases).hasViolation()).isTrue();
    }

    @Test
    void capturarUnaExcepcionGeneralNoIntroduceSql() {
        var clases = new ClassFileImporter().importClasses(CapturaGeneral.class);

        assertThat(noClasses().should().dependOnClassesThat().resideInAPackage("java.sql..")
                .evaluate(clases).hasViolation()).isFalse();
    }

    interface Operacion {
        void ejecutar() throws Exception;
    }

    static class CapturaTecnologica {
        void ejecutar(Operacion operacion) throws Exception {
            // El único vínculo con SQL es la excepción capturada; no su firma.
            try {
                operacion.ejecutar();
            } catch (SQLException excepcion) {
                throw new IllegalStateException("Fallo de la operación", excepcion);
            }
        }
    }

    static class CapturaGeneral {
        void ejecutar(Operacion operacion) {
            try {
                operacion.ejecutar();
            } catch (Exception excepcion) {
                throw new IllegalStateException("Fallo de la operación", excepcion);
            }
        }
    }
}
