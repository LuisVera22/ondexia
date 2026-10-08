package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/** Expectativas literales del negocio, independientes de los valores observados. */
class TiposDeProductoIT extends PruebaIntegracion {
    private static final String RUTA = "/api/v1/almacen/productos";
    private static final String MATRIZ = "00000000-0000-4000-8000-000000000020";
    @Autowired private ObjectMapper json;
    @Autowired private javax.sql.DataSource origen;
    private MockHttpServletRequestBuilder autenticada(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", autorizacionDemo())
                .header("X-Empresa-Id", EMPRESA_ADMINISTRADA).contentType(MediaType.APPLICATION_JSON);
    }
    private String datos(String tipo, String unidad) {
        return """
                {"nombre":"Prueba de tipo", "unidad":"%s", "afectacion":"GRAVADO",
                 "precioLista":"999999999999.123456", "tipo":"%s"}
                """.formatted(unidad, tipo);
    }
    private String crear(String tipo, String unidad) throws Exception {
        String cuerpo = datos(tipo, unidad).strip();
        cuerpo = cuerpo.substring(0, cuerpo.length() - 1) + ",\"codigo\":\"T-"
                + UUID.randomUUID().toString().substring(0, 20) + "\",\"sucursalId\":\"" + MATRIZ + "\"}";
        String respuesta = mockMvc.perform(autenticada(post(RUTA)).content(cuerpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).path("id").asString();
    }
    @Test void elTipoEsExplicitoYElPrecioConservaSeisDecimales() throws Exception {
        String id = crear("SERVICIO", "HUR");
        mockMvc.perform(autenticada(get(RUTA + "/" + id)))
                .andExpect(jsonPath("$.tipo").value("SERVICIO"))
                .andExpect(jsonPath("$.controlaStock").value(false))
                .andExpect(jsonPath("$.unidad").value("HUR"))
                .andExpect(jsonPath("$.precioLista").value("999999999999.123456"));
    }
    @Test void unidadesDeServicioAcotadasSinForzarZZ() throws Exception {
        mockMvc.perform(autenticada(get(RUTA + "/catalogos").param("tipo", "SERVICIO")))
                .andExpect(jsonPath("$.unidades[*].codigo").value(org.hamcrest.Matchers.containsInAnyOrder("ZZ", "HUR", "DAY")));
        for (String unidad : new String[]{"ZZ", "HUR", "DAY"}) crear("SERVICIO", unidad);
        String id = crear("SERVICIO", "ZZ");
        mockMvc.perform(autenticada(put(RUTA + "/" + id)).content(datos("SERVICIO", "BG")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("unidad_incompatible_con_tipo"));
        mockMvc.perform(autenticada(put(RUTA + "/" + id)).content(datos("BIEN", "ZZ")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("unidad_incompatible_con_tipo"));
    }
    @Test void conversionSinHistoriaPermitidaEnAmbosSentidos() throws Exception {
        String id = crear("SERVICIO", "DAY");
        mockMvc.perform(autenticada(put(RUTA + "/" + id)).content(datos("BIEN", "NIU")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("BIEN"));
        mockMvc.perform(autenticada(get(RUTA + "/" + id + "/existencias")))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(autenticada(put(RUTA + "/" + id)).content(datos("SERVICIO", "ZZ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("SERVICIO"));
    }
    @Test void movimientosAunqueElSaldoSeaCeroImpidenConvertir() throws Exception {
        String id = crear("BIEN", "NIU");
        String respuesta = mockMvc.perform(autenticada(post("/api/v1/almacen/almacenes"))
                .content("{\"codigo\":\"A-" + UUID.randomUUID().toString().substring(0, 8)
                        + "\",\"nombre\":\"Prueba\",\"sucursalId\":\"" + MATRIZ + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String almacen = json.readTree(respuesta).path("id").asString();
        for (int cantidad : new int[]{3, 0}) {
            mockMvc.perform(autenticada(post(RUTA + "/" + id + "/existencias/ajustes"))
                    .content("{\"almacenId\":\"" + almacen + "\",\"cantidad\":" + cantidad + "}"))
                    .andExpect(status().isOk());
            mockMvc.perform(autenticada(put(RUTA + "/" + id)).content(datos("SERVICIO", "ZZ")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("bien_con_historia_de_stock"));
        }
        mockMvc.perform(autenticada(get(RUTA + "/" + id))).andExpect(jsonPath("$.tipo").value("BIEN"));
    }
    private void insertarStockOHistoria(String id, boolean soloHistoria) throws Exception {
        try (var conexion = origen.getConnection(); var sentencia = conexion.createStatement()) {
            conexion.setAutoCommit(false);
            sentencia.execute("select set_config('ondexia.empresa_id','" + EMPRESA_ADMINISTRADA + "',true)");
            String tabla = soloHistoria ? "movimiento_stock" : "stock";
            String adicionales = soloHistoria ? ",tipo" : "";
            String valores = soloHistoria ? ", 'INGRESO'" : "";
            sentencia.execute("insert into " + tabla + " (id,empresa_id,almacen_id,producto_id,cantidad" + adicionales
                    + ") values ('" + UUID.randomUUID() + "','" + EMPRESA_ADMINISTRADA
                    + "','00000000-0000-4000-8000-000000000090','" + id + "'," + (soloHistoria ? 1 : 0) + valores + ")");
            conexion.commit();
        }
    }
    @Test void unaFilaDeStockCeroSinMovimientosImpideConvertir() throws Exception {
        String id = crear("BIEN", "NIU"); insertarStockOHistoria(id, false);
        mockMvc.perform(autenticada(put(RUTA + "/" + id)).content(datos("SERVICIO", "ZZ")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("bien_con_historia_de_stock"));
    }
    @Test void unMovimientoSinProyeccionDeStockImpideConvertir() throws Exception {
        String id = crear("BIEN", "NIU"); insertarStockOHistoria(id, true);
        mockMvc.perform(autenticada(put(RUTA + "/" + id)).content(datos("SERVICIO", "ZZ")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("bien_con_historia_de_stock"));
    }
    @Test void mostradorMixtoYListaSeparada() throws Exception {
        String servicio = crear("SERVICIO", "ZZ");
        String bien = crear("BIEN", "NIU");
        String listado = mockMvc.perform(autenticada(get(RUTA).param("tipo", "SERVICIO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(listado).contains(servicio).doesNotContain(bien);
        String mostrador = mockMvc.perform(autenticada(get(RUTA + "/disponibles").param("sucursalId", MATRIZ)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mostrador).contains(servicio, bien);
    }
}
