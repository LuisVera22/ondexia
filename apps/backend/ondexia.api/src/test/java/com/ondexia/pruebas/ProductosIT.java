package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.application.almacen.Productos;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Productos, disponibilidad por local y existencias (doc 12 §3.5), de extremo a
 * extremo. Lo que se fija: el catálogo es de la empresa y la disponibilidad del
 * local; el ajuste registra la diferencia como movimiento, y el libro no se
 * toca.
 */
class ProductosIT extends PruebaIntegracion {

    private static final String PRODUCTOS = "/api/v1/almacen/productos";
    private static final String MATRIZ = "00000000-0000-4000-8000-000000000020";
    private static final String MIRAFLORES = "00000000-0000-4000-8000-000000000021";

    @Autowired
    private Productos productos;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transaccion;

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    private MockHttpServletRequestBuilder comoAdministrador(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", autorizacionDemo())
                .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode crear(String codigo, String nombre, String precio, boolean controlaStock)
            throws Exception {
        String cuerpo = mockMvc.perform(comoAdministrador(post(PRODUCTOS)).content("""
                        {"codigo": "%s", "nombre": "%s", "unidad": "NIU", "afectacion": "GRAVADO",
                         "precioLista": %s, "controlaStock": %s, "sucursalId": "%s"}
                        """.formatted(codigo, nombre, precio, controlaStock, MATRIZ)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(cuerpo);
    }

    private String crearAlmacen(String codigo) throws Exception {
        String cuerpo = mockMvc.perform(comoAdministrador(post("/api/v1/almacen/almacenes")).content("""
                        {"codigo": "%s", "nombre": "Almacén de prueba", "sucursalId": "%s"}
                        """.formatted(codigo, MATRIZ)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(cuerpo).path("id").asString();
    }

    @Test
    @DisplayName("Alta, edición, desactivación y catálogos de SUNAT")
    void cicloCompleto() throws Exception {
        JsonNode creado = crear("cem-t01", "Cemento Portland Tipo I", "32.5", true);
        String id = creado.path("id").asString();

        assertThat(creado.path("codigo").asString()).isEqualTo("CEM-T01");
        assertThat(creado.path("unidad").asString()).isEqualTo("NIU");
        assertThat(creado.path("afectacion").asString()).isEqualTo("GRAVADO");
        assertThat(creado.path("llevaIgv").asBoolean()).isTrue();
        assertThat(creado.path("precioLista").decimalValue()).isEqualByComparingTo("32.5");

        mockMvc.perform(comoAdministrador(put(PRODUCTOS + "/" + id)).content("""
                        {"nombre": "Cemento Portland Tipo I 42.5 kg", "unidad": "BG",
                         "afectacion": "EXONERADO", "precioLista": 33, "controlaStock": true}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("CEM-T01"))
                .andExpect(jsonPath("$.unidad").value("BG"))
                .andExpect(jsonPath("$.unidadNombre").value("Bolsa"))
                .andExpect(jsonPath("$.llevaIgv").value(false));

        mockMvc.perform(comoAdministrador(put(PRODUCTOS + "/" + id + "/estado")).content("{\"activo\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(comoAdministrador(get(PRODUCTOS + "/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.nombre").value("Cemento Portland Tipo I 42.5 kg"));

        mockMvc.perform(comoAdministrador(get(PRODUCTOS + "/catalogos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidades[?(@.codigo == 'NIU')].nombre").value("Unidad"))
                .andExpect(jsonPath("$.afectaciones.length()").value(3));
    }

    @Test
    @DisplayName("El código es único por empresa y la unidad tiene que estar en el catálogo")
    void validaciones() throws Exception {
        crear("DUP-P", "Uno", "1", true);

        mockMvc.perform(comoAdministrador(post(PRODUCTOS)).content("""
                        {"codigo": "dup-p", "nombre": "Otro", "unidad": "NIU", "afectacion": "GRAVADO",
                         "precioLista": 1, "sucursalId": "%s"}
                        """.formatted(MATRIZ)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("codigo_duplicado"))
                .andExpect(jsonPath("$.campos.codigo").exists());

        mockMvc.perform(comoAdministrador(post(PRODUCTOS)).content("""
                        {"codigo": "X-1", "nombre": "Otro", "unidad": "XYZ", "afectacion": "GRAVADO",
                         "precioLista": 1, "sucursalId": "%s"}
                        """.formatted(MATRIZ)))
                .andExpect(status().isBadRequest());

        // Sin establecimiento no hay producto: sería un borrador que nadie ve.
        mockMvc.perform(comoAdministrador(post(PRODUCTOS)).content("""
                        {"codigo": "X-2", "nombre": "Otro", "unidad": "NIU", "afectacion": "GRAVADO",
                         "precioLista": 1}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.sucursalId").exists());
    }

    @Test
    @DisplayName("Nace disponible solo en el local donde se creó; los demás se fijan a mano, con precio propio")
    void disponibilidadPorLocal() throws Exception {
        String id = crear("DISP-1", "Disponible", "10", true).path("id").asString();

        mockMvc.perform(comoAdministrador(get(PRODUCTOS + "/" + id + "/locales")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sucursalId").value(MATRIZ))
                .andExpect(jsonPath("$[0].disponible").value(true))
                .andExpect(jsonPath("$[0].precio").doesNotExist());

        mockMvc.perform(comoAdministrador(put(PRODUCTOS + "/" + id + "/locales/" + MIRAFLORES))
                        .content("{\"disponible\": true, \"precio\": 9.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucursalId").value(MIRAFLORES))
                .andExpect(jsonPath("$.precio").value(9.5));

        // Y se puede retirar de un local sin borrar nada.
        mockMvc.perform(comoAdministrador(put(PRODUCTOS + "/" + id + "/locales/" + MATRIZ))
                        .content("{\"disponible\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false));

        // Un local de otra empresa no vale.
        mockMvc.perform(comoAdministrador(put(PRODUCTOS + "/" + id + "/locales/"
                        + "00000000-0000-4000-8000-000000000022"))
                        .content("{\"disponible\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("establecimiento_invalido"));
    }

    @Test
    @DisplayName("El ajuste anota la diferencia en el libro y la proyección la sigue")
    void ajusteDeExistencias() throws Exception {
        String producto = crear("STK-1", "Con existencias", "5", true).path("id").asString();
        String almacen = crearAlmacen("ALM-STK");

        mockMvc.perform(comoAdministrador(get(PRODUCTOS + "/" + producto + "/existencias")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Primer conteo: 120. Había 0, así que el movimiento es +120.
        mockMvc.perform(comoAdministrador(post(PRODUCTOS + "/" + producto + "/existencias/ajustes"))
                        .content("{\"almacenId\": \"%s\", \"cantidad\": 120, \"motivo\": \"Inventario inicial\"}"
                                .formatted(almacen)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.almacenId").value(almacen))
                .andExpect(jsonPath("$.cantidad").value(120));

        // Segundo conteo: 115. El movimiento es -5, no «115».
        mockMvc.perform(comoAdministrador(post(PRODUCTOS + "/" + producto + "/existencias/ajustes"))
                        .content("{\"almacenId\": \"%s\", \"cantidad\": 115}".formatted(almacen)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(115));

        // Contar lo mismo no mueve nada.
        mockMvc.perform(comoAdministrador(post(PRODUCTOS + "/" + producto + "/existencias/ajustes"))
                        .content("{\"almacenId\": \"%s\", \"cantidad\": 115}".formatted(almacen)))
                .andExpect(status().isOk());

        String movimientos = mockMvc.perform(comoAdministrador(get(PRODUCTOS + "/" + producto + "/movimientos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode libro = json.readTree(movimientos);
        // Del más reciente al más antiguo.
        assertThat(libro.get(0).path("cantidad").decimalValue()).isEqualByComparingTo("-5");
        assertThat(libro.get(0).path("tipo").asString()).isEqualTo("AJUSTE");
        assertThat(libro.get(0).path("usuarioId").asString()).isEqualTo(USUARIO_DEMO);
        assertThat(libro.get(1).path("cantidad").decimalValue()).isEqualByComparingTo("120");
        assertThat(libro.get(1).path("motivo").asString()).isEqualTo("Inventario inicial");

        mockMvc.perform(comoAdministrador(get(PRODUCTOS + "/" + producto + "/existencias")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cantidad").value(115));

        // El libro es de solo inserción, por disparador.
        ContextoDePrueba.comoUsuarioDe(UUID.fromString(USUARIO_DEMO), CUENTA_DEMO,
                UUID.fromString(EMPRESA_ADMINISTRADA));
        // Dentro de una transacción: es el gestor quien fija la empresa del RLS,
        // y sin ella el borrado no vería filas y «acertaría» sin borrar nada.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                transaccion.executeWithoutResult(estado ->
                        jdbc.sql("delete from movimiento_stock where producto_id = ?::uuid")
                                .param(producto).update()))
                .hasMessageContaining("solo insercion");
    }

    @Test
    @DisplayName("Un servicio no tiene existencias que ajustar")
    void servicioSinExistencias() throws Exception {
        String servicio = crear("SRV-1", "Instalación", "50", false).path("id").asString();
        String almacen = crearAlmacen("ALM-SRV");

        mockMvc.perform(comoAdministrador(post(PRODUCTOS + "/" + servicio + "/existencias/ajustes"))
                        .content("{\"almacenId\": \"%s\", \"cantidad\": 3}".formatted(almacen)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("producto_sin_existencias"));
    }

    @Test
    @DisplayName("Se busca por código o por nombre, sin mayúsculas")
    void busqueda() throws Exception {
        crear("BUSQ-1", "Fierro corrugado media pulgada", "48", true);
        crear("BUSQ-2", "Alambre negro", "6.8", true);

        mockMvc.perform(comoAdministrador(get(PRODUCTOS).param("q", "fierro")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'BUSQ-1')]").exists())
                .andExpect(jsonPath("$[?(@.codigo == 'BUSQ-2')]").doesNotExist());

        mockMvc.perform(comoAdministrador(get(PRODUCTOS).param("q", "busq-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("BUSQ-2"));
    }

    @Test
    @DisplayName("El catálogo de una empresa no se ve desde otra")
    void aislamiento() throws Exception {
        crear("SOLO-A-P", "Solo de A", "1", true);

        ContextoDePrueba.comoUsuarioDe(UUID.fromString(USUARIO_DEMO), CUENTA_DEMO,
                UUID.fromString(EMPRESA_COMO_VENDEDOR));
        assertThat(productos.listar()).noneMatch(p -> "SOLO-A-P".equals(p.codigo()));

        ContextoDePrueba.comoUsuarioDe(UUID.fromString(USUARIO_DEMO), CUENTA_DEMO,
                UUID.fromString(EMPRESA_ADMINISTRADA));
        assertThat(productos.listar()).anyMatch(p -> "SOLO-A-P".equals(p.codigo()));
    }
}
