package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import com.ondexia.infrastructure.seguridad.ContextoInterceptor;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import com.ondexia.domain.identidad.PermisoRepositorio;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Que {@code @RequierePermiso} de verdad autorice, y que sus valores existan.
 *
 * <h2>Por qué esto no es una prueba de adorno</h2>
 *
 * La anotación funciona por sustitución de plantilla: Spring Security reemplaza
 * <code>{modulo}</code> y <code>{accion}</code> por los valores de cada uso,
 * pero solo si existe el bean {@code AnnotationTemplateExpressionDefaults}.
 *
 * <p>Sin ese bean <strong>no hay ningún error</strong>. La expresión llega
 * literal, el evaluador busca un permiso llamado <code>{modulo}:{accion}</code>,
 * no lo encuentra y deniega. Todos los endpoints devuelven 403 y el sistema
 * parece «muy seguro». Es el tipo de fallo que se atribuye a cualquier otra
 * cosa antes que a su causa.
 */
@Import(PermisosAnotacionIT.ControladorDePrueba.class)
class PermisosAnotacionIT extends PruebaIntegracion {

    /**
     * Controlador que solo existe en pruebas.
     *
     * <p>En la Entrega 0 todavía no hay ningún endpoint de negocio con
     * permisos, y esperar a que lo haya significaría no verificar el mecanismo
     * hasta después de haberlo usado en veinte sitios.
     */
    @TestConfiguration
    static class ControladorDePrueba {

        /**
         * Spring registra sola esta clase anidada por llevar {@code @RestController}
         * sobre una clase de configuración. Declararla ADEMÁS con un
         * {@code @Bean} la registra dos veces y produce «Ambiguous mapping» al
         * arrancar — el contexto entero falla, no solo esta prueba.
         */
        @RestController
        @RequestMapping("/pruebas/permisos")
        static class Controlador {

            @RequierePermiso(modulo = "ventas.comprobante", accion = "anular")
            @GetMapping("/anular")
            String anular() {
                return "anulado";
            }

            @RequierePermiso(modulo = "ventas.comprobante", accion = "emitir")
            @GetMapping("/emitir")
            String emitir() {
                return "emitido";
            }
        }
    }

    @Autowired
    private PermisoRepositorio permisos;

    @Test
    @DisplayName("El permiso se comprueba contra el rol EN la empresa activa")
    void elPermisoDependeDeLaEmpresaActiva() throws Exception {
        String token = autorizacionDemo();

        // Administrador en la primera empresa: puede anular.
        mockMvc.perform(get("/pruebas/permisos/anular")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(ContextoInterceptor.CABECERA_EMPRESA, EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk());

        // Mismo usuario, mismo token, otra empresa: es Vendedor y no puede.
        // Si la sustitución de plantilla no funcionara, la primera llamada
        // también habría dado 403 y esta prueba fallaría antes de llegar aquí.
        mockMvc.perform(get("/pruebas/permisos/anular")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(ContextoInterceptor.CABECERA_EMPRESA, EMPRESA_COMO_VENDEDOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("acceso_denegado"));

        // Pero emitir sí puede: la separación entre emitir y anular es la más
        // importante del catálogo de permisos.
        mockMvc.perform(get("/pruebas/permisos/emitir")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(ContextoInterceptor.CABECERA_EMPRESA, EMPRESA_COMO_VENDEDOR))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sin empresa activa no se pasa, y se dice por qué")
    void sinEmpresaActivaNoSePasa() throws Exception {
        /*
         * Esta prueba exigía un 403 y ahora exige un 400. El cambio es
         * deliberado, y lo motivó la Entrega 1.
         *
         * Lo esencial no ha cambiado: sin empresa activa no se pasa, y se falla
         * cerrado. Lo que cambia es la RAZÓN que se comunica. Un 403 decía «no
         * tienes permiso», y el frontend lo traduce llevando al usuario a la
         * pantalla de «sin permisos» — cuando lo único que ocurre es que tiene
         * dos empresas y aún no ha elegido sobre cuál trabaja.
         *
         * Ese usuario acababa expulsado a una pantalla que le decía algo falso
         * sobre sus permisos. Ahora recibe un código propio con el que el SPA
         * puede pedirle que elija.
         */
        mockMvc.perform(get("/pruebas/permisos/anular")
                        .header(HttpHeaders.AUTHORIZATION, autorizacionDemo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("sin_empresa_activa"));
    }

    @Test
    @DisplayName("Todo permiso declarado en el código existe en el catálogo")
    void losPermisosDeclaradosExisten() {
        // Una errata en `modulo = "almacn.producto"` compila, arranca y deniega
        // siempre. Nadie lo nota hasta que un usuario reporta que no puede
        // hacer algo que sí debería. Esta comprobación lo convierte en un fallo
        // de build.
        JavaClasses clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ondexia");

        Set<String> enElCatalogo = permisos.listarCatalogo().stream()
                .map(com.ondexia.domain.identidad.Permiso::codigo)
                .collect(java.util.stream.Collectors.toSet());

        List<String> declarados = clases.stream()
                .flatMap(clase -> clase.getMethods().stream())
                .filter(metodo -> metodo.isAnnotatedWith(RequierePermiso.class))
                .map(metodo -> metodo.getAnnotationOfType(RequierePermiso.class))
                .map(anotacion -> anotacion.modulo() + ":" + anotacion.accion())
                .distinct()
                .toList();

        assertThat(declarados)
                .as("permisos declarados con @RequierePermiso que no están en la migración V2")
                .allSatisfy(codigo -> assertThat(enElCatalogo).contains(codigo));
    }
}
