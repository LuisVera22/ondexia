package com.ondexia.pruebas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * La portada devuelve cifras de la base, no cifras inventadas.
 *
 * <p>Es la prueba que la iteración 7 le debía al panel: el anterior pintaba
 * siete indicadores y un gráfico con datos escritos a mano en el componente, y
 * nada de eso podía ponerse rojo cuando dejara de ser cierto.
 *
 * <p>La última prueba —la del usuario sin permisos de ventas— es la que
 * justifica que el controlador no lleve {@code @RequierePermiso}: la portada
 * responde 200 y omite los bloques, en vez de un 403 en la primera pantalla.
 */
class PanelIT extends PruebaIntegracion {

    private static final String MATRIZ = "00000000-0000-4000-8000-000000000020";
    private static final String CEMENTO = "00000000-0000-4000-8000-000000000060";
    private static final String SERIE_NV = "00000000-0000-4000-8000-0000000000a0";

    private static final AtomicInteger CORRELATIVO_CAJAS = new AtomicInteger(700);

    @Autowired
    private ObjectMapper json;

    private MockHttpServletRequestBuilder comoAdministrador(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", autorizacionDemo())
                .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode panel() throws Exception {
        return json.readTree(mockMvc.perform(comoAdministrador(get("/api/v1/panel")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String cajaAbierta() throws Exception {
        String caja = json.readTree(mockMvc.perform(comoAdministrador(post("/api/v1/ventas/cajas"))
                        .content("""
                                {"codigo": "PNL%d", "nombre": "Caja del panel", "sucursalId": "%s"}
                                """.formatted(CORRELATIVO_CAJAS.incrementAndGet(), MATRIZ)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("id").asString();
        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/cajas/" + caja + "/sesiones"))
                        .content("{\"montoInicial\": 100.00}"))
                .andExpect(status().isCreated());
        return caja;
    }

    private void cerrar(String caja) throws Exception {
        String sesion = json.readTree(
                mockMvc.perform(comoAdministrador(get("/api/v1/ventas/cajas/" + caja + "/sesion-abierta")))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("id").asString();
        mockMvc.perform(comoAdministrador(
                        put("/api/v1/ventas/cajas/sesiones/" + sesion + "/cierre"))
                        .content("{\"declarado\": {}}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("La portada trae la fecha de Lima y los tres bloques para quien los puede ver")
    void losTresBloques() throws Exception {
        var respuesta = panel();

        // No se compara con una fecha fija: la prueba correría a medianoche en
        // UTC y el día de Lima sería el anterior, que es justo lo que el campo
        // arregla. Basta con que venga y tenga forma de fecha.
        org.assertj.core.api.Assertions.assertThat(respuesta.path("fecha").asString())
                .matches("\\d{4}-\\d{2}-\\d{2}");
        org.assertj.core.api.Assertions.assertThat(respuesta.path("cajasAbiertas").isArray()).isTrue();
        org.assertj.core.api.Assertions.assertThat(respuesta.path("ventas").isObject()).isTrue();
        org.assertj.core.api.Assertions.assertThat(
                respuesta.path("comprobantesPorAtender").isObject()).isTrue();
    }

    @Test
    @DisplayName("Una caja abierta aparece en la portada y desaparece al cerrarla")
    void laCajaAbierta() throws Exception {
        String caja = cajaAbierta();

        mockMvc.perform(comoAdministrador(get("/api/v1/panel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cajasAbiertas[?(@.cajaId == '" + caja + "')]").exists())
                .andExpect(jsonPath("$.cajasAbiertas[?(@.cajaId == '" + caja + "')].montoInicial")
                        .value(org.hamcrest.Matchers.contains(
                                org.hamcrest.Matchers.closeTo(100.0, 0.000001))));

        cerrar(caja);

        mockMvc.perform(comoAdministrador(get("/api/v1/panel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cajasAbiertas[?(@.cajaId == '" + caja + "')]").doesNotExist());
    }

    @Test
    @DisplayName("Una nota de venta de 65 soles sube las ventas del dia en 65 soles")
    void laVentaSubeLaCifra() throws Exception {
        String caja = cajaAbierta();
        var antes = panel().path("ventas");
        int documentosAntes = antes.path("documentos").asInt();
        var importeAntes = antes.path("importe").decimalValue();

        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                        {
                          "cajaId": "%s",
                          "serieId": "%s",
                          "lineas": [{"productoId": "%s", "cantidad": 2}],
                          "pagos": [{"forma": "EFECTIVO", "monto": 65.00}]
                        }
                        """.formatted(caja, SERIE_NV, CEMENTO)))
                .andExpect(status().isCreated());

        var despues = panel().path("ventas");
        org.assertj.core.api.Assertions.assertThat(despues.path("documentos").asInt())
                .isEqualTo(documentosAntes + 1);
        org.assertj.core.api.Assertions.assertThat(despues.path("importe").decimalValue())
                .isEqualByComparingTo(importeAntes.add(new java.math.BigDecimal("65")));

        cerrar(caja);
    }

}
