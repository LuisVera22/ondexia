package com.ondexia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Escribe el contrato OpenAPI en {@code ondexia.contracts/openapi.yaml}.
 *
 * <h2>Por que es una prueba y no un plugin de Maven</h2>
 *
 * El plugin oficial de springdoc arranca la aplicacion en una fase aparte del
 * build solo para consultarla. Esta prueba ya tiene la aplicacion arrancada y el
 * contenedor de base de datos en pie, asi que exportar el contrato le cuesta una
 * peticion. Un paso menos en el build y una pieza menos que mantener.
 *
 * <h2>Por que una prueba escribe en el repositorio</h2>
 *
 * Es deliberado y conviene decirlo, porque normalmente seria mala idea. El
 * efecto es que <strong>el contrato no puede quedarse atras</strong>: se
 * regenera con cada ejecucion de la suite, y si cambia, aparece en
 * {@code git status} junto al cambio que lo provoco. La alternativa —acordarse
 * de regenerarlo— es exactamente el fallo que el contrato existe para evitar
 * (doc 03 §5).
 *
 * <p>De este archivo se genera el cliente Angular. Un campo renombrado en el
 * backend y no propagado al frontend aparece al compilar, no en produccion
 * semanas despues.
 */
class ExportarContratoIT extends PruebaIntegracion {

    /** Desde {@code apps/ondexia.api}, que es el directorio de trabajo del build. */
    private static final Path DESTINO = Path.of("..", "..", "ondexia.contracts", "openapi.yaml");

    @Test
    @DisplayName("El contrato se exporta a ondexia.contracts")
    void exportarContrato() throws Exception {
        String contrato = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                // Con el charset explicito. La respuesta YAML de springdoc no
                // declara ninguno, y sin este argumento MockMvc decodifica con
                // ISO-8859-1: los guiones largos y los simbolos de seccion salen
                // como pares de caracteres rotos en el contrato publicado.
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(contrato)
                .as("el contrato no puede salir vacio")
                .contains("/api/v1/contexto");

        Path destino = DESTINO.toAbsolutePath().normalize();
        Files.createDirectories(destino.getParent());

        String encabezado = """
                # GENERADO AUTOMATICAMENTE — no editar a mano.
                #
                # Lo escribe ExportarContratoIT en cada ejecucion de la suite, a partir de los
                # controladores de ondexia-api. Si este archivo aparece modificado en git status,
                # es porque la API cambio: revisa el diff antes de confirmar, porque de aqui se
                # genera el cliente Angular.
                #
                # Regenerar:  cd apps && ./mvnw test
                #
                # El -am de "-pl ondexia.api -am" no es opcional si acotas el modulo: sin el,
                # Maven no construye ondexia-domain y la resolucion falla.
                """;

        Files.writeString(destino, encabezado + contrato, StandardCharsets.UTF_8);
    }
}
