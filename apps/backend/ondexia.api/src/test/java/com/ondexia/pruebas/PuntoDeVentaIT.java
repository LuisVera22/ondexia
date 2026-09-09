package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
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
 * El punto de venta de extremo a extremo (doc 12 §8, iteración 4): correlativo,
 * líneas con IGV, pago mixto, descarga de existencias y el arqueo que suma los
 * cobros. Y las tres reglas de §3.2, esta vez a través de la API.
 *
 * <p>Sobre los datos de ejemplo de la V900: la matriz tiene almacén, series
 * N001/B001/F001, tres productos y dos clientes. Cada prueba abre su propia caja
 * para no depender del estado que dejó otra.
 */
class PuntoDeVentaIT extends PruebaIntegracion {

    private static final String MATRIZ = "00000000-0000-4000-8000-000000000020";
    private static final String ALMACEN_MATRIZ = "00000000-0000-4000-8000-000000000090";
    private static final String CEMENTO = "00000000-0000-4000-8000-000000000060";
    private static final String FIERRO = "00000000-0000-4000-8000-000000000061";
    private static final String INSTALACION = "00000000-0000-4000-8000-000000000062";
    private static final String CLIENTE_DNI = "00000000-0000-4000-8000-000000000080";
    private static final String CLIENTE_RUC = "00000000-0000-4000-8000-000000000081";
    /** Las series de la V900. Se indican siempre: otras pruebas crean más del mismo tipo. */
    private static final String SERIE_NV = "00000000-0000-4000-8000-0000000000a0";
    private static final String SERIE_BOLETA = "00000000-0000-4000-8000-0000000000a1";
    private static final String SERIE_FACTURA = "00000000-0000-4000-8000-0000000000a2";

    private static final AtomicInteger CORRELATIVO_CAJAS = new AtomicInteger();

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void limpiar() {
        // Se restituye el valor POR OMISION, que desde la V23 es no dejar vender
        // sin existencias. Dejarlo en `true` haria que la prueba de abajo pasara
        // por herencia de otra en vez de por lo que comprueba.
        jdbc.sql("update empresa set permite_venta_sin_stock = false where id = ?::uuid")
                .param(EMPRESA_ADMINISTRADA).update();
    }

    private MockHttpServletRequestBuilder comoAdministrador(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", autorizacionDemo())
                .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode leer(String cuerpo) {
        return json.readTree(cuerpo);
    }

    /** Una caja nueva en la matriz, abierta con el monto inicial dado. */
    private String cajaAbierta(String montoInicial) throws Exception {
        String caja = leer(mockMvc.perform(comoAdministrador(post("/api/v1/ventas/cajas")).content("""
                        {"codigo": "POS%d", "nombre": "Caja de prueba", "sucursalId": "%s"}
                        """.formatted(CORRELATIVO_CAJAS.incrementAndGet(), MATRIZ)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("id").asString();
        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/cajas/" + caja + "/sesiones"))
                        .content("{\"montoInicial\": " + montoInicial + "}"))
                .andExpect(status().isCreated());
        return caja;
    }

    private void contar(String producto, String cantidad) throws Exception {
        mockMvc.perform(comoAdministrador(post("/api/v1/almacen/productos/" + producto + "/existencias/ajustes"))
                        .content("{\"almacenId\": \"%s\", \"cantidad\": %s, \"motivo\": \"Conteo de prueba\"}"
                                .formatted(ALMACEN_MATRIZ, cantidad)))
                .andExpect(status().isOk());
    }

    private String existencia(String producto) throws Exception {
        JsonNode existencias = leer(mockMvc.perform(comoAdministrador(
                        get("/api/v1/almacen/productos/" + producto + "/existencias")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode e : existencias) {
            if (ALMACEN_MATRIZ.equals(e.path("almacenId").asString())) {
                return e.path("cantidad").decimalValue().stripTrailingZeros().toPlainString();
            }
        }
        return "0";
    }

    @Test
    @DisplayName("Nota de venta completa: correlativo, IGV, pago mixto, descarga y arqueo")
    void notaDeVentaCompleta() throws Exception {
        String caja = cajaAbierta("100");
        contar(CEMENTO, "10");

        // Dos bolsas de cemento a 32.50 (65.00) y una instalación a 80: total
        // 145.00, cobrado 100 en efectivo y 45 con tarjeta.
        String cuerpo = mockMvc.perform(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                        {"cajaId": "%s", "serieId": "%s",
                         "lineas": [{"productoId": "%s", "cantidad": 2}, {"productoId": "%s", "cantidad": 1}],
                         "pagos": [{"forma": "EFECTIVO", "monto": 100.00},
                                   {"forma": "TARJETA", "monto": 45.00, "referencia": "VISA 4321"}],
                         "observaciones": "Entrega mañana"}
                        """.formatted(caja, SERIE_NV, CEMENTO, INSTALACION)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documento.tipo").value("NV"))
                .andExpect(jsonPath("$.documento.fiscal").value(false))
                .andExpect(jsonPath("$.documento.estado").value("EMITIDO"))
                .andExpect(jsonPath("$.documento.numeroCompleto").value(org.hamcrest.Matchers.startsWith("N001-")))
                .andExpect(jsonPath("$.documento.cliente").doesNotExist())
                .andExpect(jsonPath("$.documento.lineas.length()").value(2))
                .andExpect(jsonPath("$.documento.pagos.length()").value(2))
                .andExpect(jsonPath("$.avisos.length()").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode documento = leer(cuerpo).path("documento");

        assertThat(documento.path("total").decimalValue()).isEqualByComparingTo("145.00");
        assertThat(documento.path("totalIgv").decimalValue()).isEqualByComparingTo("22.12");
        assertThat(documento.path("totalGravado").decimalValue()).isEqualByComparingTo("122.88");
        // La descripción y la unidad se copian del producto.
        assertThat(documento.path("lineas").get(0).path("descripcion").asString())
                .isEqualTo("Cemento Portland Tipo I 42.5 kg");
        assertThat(documento.path("lineas").get(0).path("unidad").asString()).isEqualTo("BG");
        assertThat(documento.path("lineas").get(0).path("precioUnitario").decimalValue())
                .isEqualByComparingTo("32.5");

        // Descargó las dos bolsas; la instalación no controla existencias.
        assertThat(existencia(CEMENTO)).isEqualTo("8");
        JsonNode movimientos = leer(mockMvc.perform(comoAdministrador(
                        get("/api/v1/almacen/productos/" + CEMENTO + "/movimientos")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(movimientos.get(0).path("tipo").asString()).isEqualTo("VENTA");
        assertThat(movimientos.get(0).path("documentoId").asString())
                .isEqualTo(documento.path("id").asString());
        assertThat(movimientos.get(0).path("cantidad").decimalValue()).isEqualByComparingTo("-2");

        // Se lee de vuelta y aparece en el listado de notas de venta.
        mockMvc.perform(comoAdministrador(get("/api/v1/ventas/notas-de-venta/" + documento.path("id").asString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observaciones").value("Entrega mañana"))
                .andExpect(jsonPath("$.pagos[1].referencia").value("VISA 4321"));
        mockMvc.perform(comoAdministrador(get("/api/v1/ventas/notas-de-venta")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(documento.path("id").asString()));
        // Y no entre los comprobantes: no es uno.
        mockMvc.perform(comoAdministrador(get("/api/v1/ventas/comprobantes/" + documento.path("id").asString())))
                .andExpect(status().isNotFound());

        // El arqueo suma los cobros: efectivo 100 inicial + 100 cobrado; tarjeta 45.
        String sesion = leer(mockMvc.perform(comoAdministrador(get("/api/v1/ventas/cajas/" + caja + "/sesion-abierta")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("id").asString();
        JsonNode arqueo = leer(mockMvc.perform(comoAdministrador(put("/api/v1/ventas/cajas/sesiones/" + sesion + "/cierre"))
                        .content("{\"declarado\": {\"EFECTIVO\": 200, \"TARJETA\": 45.00}}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(arqueo.path("calculado").path("EFECTIVO").decimalValue()).isEqualByComparingTo("200");
        assertThat(arqueo.path("calculado").path("TARJETA").decimalValue()).isEqualByComparingTo("45.00");
        assertThat(arqueo.path("diferencia").path("EFECTIVO").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Una boleta de más de S/ 700 sin adquirente no se emite; con DNI queda pendiente de SUNAT")
    void boletaDeMasDe700() throws Exception {
        String caja = cajaAbierta("0");
        // Hay existencias de sobra: lo que esta prueba mira es el adquirente de
        // la boleta, no el almacen, y desde la V23 vender sin stock se rechaza.
        contar(FIERRO, "40");
        // Veinte fierros a 48: S/ 960.
        String venta = """
                {"tipo": "BOLETA", "venta": {"cajaId": "%s", %s "serieId": "%s",
                 "lineas": [{"productoId": "%s", "cantidad": 20}],
                 "pagos": [{"forma": "TRANSFERENCIA", "monto": 960.00, "referencia": "OP 778812"}]}}
                """;

        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/comprobantes"))
                        .content(venta.formatted(caja, "", SERIE_BOLETA, FIERRO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("boleta_exige_adquirente"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("700")))
                .andExpect(jsonPath("$.campos.clienteId").exists());

        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/comprobantes"))
                        .content(venta.formatted(caja, "\"clienteId\": \"" + CLIENTE_DNI + "\",", SERIE_BOLETA, FIERRO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documento.tipo").value("03"))
                .andExpect(jsonPath("$.documento.fiscal").value(true))
                .andExpect(jsonPath("$.documento.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.documento.numeroCompleto").value(org.hamcrest.Matchers.startsWith("B001-")))
                .andExpect(jsonPath("$.documento.cliente.numeroDocumento").value("70123456"));
    }

    @Test
    @DisplayName("Una factura exige un cliente con RUC")
    void facturaExigeRuc() throws Exception {
        String caja = cajaAbierta("0");
        String venta = """
                {"tipo": "FACTURA", "venta": {"cajaId": "%s", "clienteId": "%s", "serieId": "%s",
                 "lineas": [{"productoId": "%s", "cantidad": 1}],
                 "pagos": [{"forma": "EFECTIVO", "monto": 80.00}]}}
                """;

        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/comprobantes"))
                        .content(venta.formatted(caja, CLIENTE_DNI, SERIE_FACTURA, INSTALACION)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("factura_sin_ruc"));

        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/comprobantes"))
                        .content(venta.formatted(caja, CLIENTE_RUC, SERIE_FACTURA, INSTALACION)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documento.tipo").value("01"))
                .andExpect(jsonPath("$.documento.numeroCompleto").value(org.hamcrest.Matchers.startsWith("F001-")))
                .andExpect(jsonPath("$.documento.cliente.tipoDocumento").value("RUC"));

        mockMvc.perform(comoAdministrador(get("/api/v1/ventas/comprobantes").param("tipo", "FACTURA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("01"));
    }

    @Test
    @DisplayName("Sin caja abierta no se vende, y los pagos tienen que cuadrar")
    void cajaCerradaYPagos() throws Exception {
        String cerrada = leer(mockMvc.perform(comoAdministrador(post("/api/v1/ventas/cajas")).content("""
                        {"codigo": "POSC%d", "nombre": "Cerrada", "sucursalId": "%s"}
                        """.formatted(CORRELATIVO_CAJAS.incrementAndGet(), MATRIZ)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("id").asString();

        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                        {"cajaId": "%s", "serieId": "%s", "lineas": [{"productoId": "%s", "cantidad": 1}],
                         "pagos": [{"forma": "EFECTIVO", "monto": 80.00}]}
                        """.formatted(cerrada, SERIE_NV, INSTALACION)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("caja_cerrada"));

        String abierta = cajaAbierta("0");
        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                        {"cajaId": "%s", "serieId": "%s", "lineas": [{"productoId": "%s", "cantidad": 1}],
                         "pagos": [{"forma": "EFECTIVO", "monto": 79.00}]}
                        """.formatted(abierta, SERIE_NV, INSTALACION)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("pagos_no_cuadran"))
                .andExpect(jsonPath("$.campos.pagos").exists());

        // Y el correlativo no se gastó en los intentos fallidos: la siguiente
        // venta válida toma justo el número que la API anunciaba como siguiente.
        JsonNode series = leer(mockMvc.perform(comoAdministrador(get("/api/v1/ventas/series"))
                        .param("tipo", "NOTA_VENTA").param("sucursalId", MATRIZ))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String siguiente = "";
        for (JsonNode s : series) {
            if (SERIE_NV.equals(s.path("id").asString())) {
                siguiente = s.path("siguienteNumero").asString();
            }
        }
        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                        {"cajaId": "%s", "serieId": "%s", "lineas": [{"productoId": "%s", "cantidad": 1}],
                         "pagos": [{"forma": "EFECTIVO", "monto": 80.00}]}
                        """.formatted(abierta, SERIE_NV, INSTALACION)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documento.numeroCompleto").value(siguiente));
    }

    @Test
    @DisplayName("Vender sin existencias se avisa si la empresa lo permite; si no, se rechaza")
    void ventaSinExistencias() throws Exception {
        String caja = cajaAbierta("0");
        contar(FIERRO, "1");

        // Se enciende a proposito: desde la V23 el valor de omision es el
        // contrario, y esta prueba es la del camino permisivo.
        mockMvc.perform(comoAdministrador(put("/api/v1/configuracion/empresa")).content("""
                        {"nombreComercial": "Demo", "permiteVentaSinStock": true}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permiteVentaSinStock").value(true));

        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                        {"cajaId": "%s", "serieId": "%s", "lineas": [{"productoId": "%s", "cantidad": 3}],
                         "pagos": [{"forma": "EFECTIVO", "monto": 144.00}]}
                        """.formatted(caja, SERIE_NV, FIERRO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avisos.length()").value(1))
                .andExpect(jsonPath("$.avisos[0]").value(org.hamcrest.Matchers.containsString("-2")));
        assertThat(existencia(FIERRO)).isEqualTo("-2");

        // La empresa lo prohíbe desde su ficha.
        mockMvc.perform(comoAdministrador(put("/api/v1/configuracion/empresa")).content("""
                        {"nombreComercial": "Demo", "permiteVentaSinStock": false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permiteVentaSinStock").value(false));

        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                        {"cajaId": "%s", "serieId": "%s", "lineas": [{"productoId": "%s", "cantidad": 1}],
                         "pagos": [{"forma": "EFECTIVO", "monto": 48.00}]}
                        """.formatted(caja, SERIE_NV, FIERRO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("existencias_insuficientes"));
        // Nada se descargó en el intento rechazado.
        assertThat(existencia(FIERRO)).isEqualTo("-2");
    }

    @Test
    @DisplayName("Sin tocar nada, la empresa nace sin poder vender lo que no tiene")
    void sinExistenciasBloqueadoDeOrigen() throws Exception {
        // Observación del propietario del 2026-09-08: el bloqueo existía y venía
        // apagado. Lo que se fija aquí es el valor de omisión, no el mecanismo
        // —ese ya lo prueba ventaSinExistencias—, porque el defecto estaba en
        // que nadie lo encendía.
        mockMvc.perform(comoAdministrador(get("/api/v1/configuracion/empresa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permiteVentaSinStock").value(false));

        String caja = cajaAbierta("0");
        contar(FIERRO, "1");

        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                        {"cajaId": "%s", "serieId": "%s", "lineas": [{"productoId": "%s", "cantidad": 2}],
                         "pagos": [{"forma": "EFECTIVO", "monto": 96.00}]}
                        """.formatted(caja, SERIE_NV, FIERRO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("existencias_insuficientes"));
        assertThat(existencia(FIERRO)).isEqualTo("1");
    }

    @Test
    @DisplayName("Un billete de 100 sobre 98: el comprobante dice 98 y la caja devuelve 2")
    void vueltoEnEfectivo() throws Exception {
        String caja = cajaAbierta("50");
        contar(CEMENTO, "10");

        // Tres bolsas a 32.50 son 97.50. El cliente paga con un billete de 100.
        String cuerpo = mockMvc.perform(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                        {"cajaId": "%s", "serieId": "%s", "lineas": [{"productoId": "%s", "cantidad": 3}],
                         "pagos": [{"forma": "EFECTIVO", "monto": 97.50, "entregado": 100.00}]}
                        """.formatted(caja, SERIE_NV, CEMENTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documento.pagos[0].entregado").value(100.00))
                .andExpect(jsonPath("$.documento.pagos[0].vuelto").value(2.50))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // Lo que va al comprobante es la venta, no el billete.
        assertThat(leer(cuerpo).path("documento").path("total").decimalValue())
                .isEqualByComparingTo("97.50");

        // Y el arqueo cuadra con el IMPORTE cobrado, no con el billete: 50 de
        // monto inicial mas 97.50 son 147.50, y no 150. Es la razon de que el
        // vuelto viva aparte del monto.
        String sesionId = leer(mockMvc.perform(comoAdministrador(
                        get("/api/v1/ventas/cajas/" + caja + "/sesion-abierta")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("id").asString();

        mockMvc.perform(comoAdministrador(put("/api/v1/ventas/cajas/sesiones/" + sesionId + "/cierre"))
                        .content("""
                                {"declarado": {"EFECTIVO": 147.50}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculado.EFECTIVO").value(147.50))
                .andExpect(jsonPath("$.diferencia.EFECTIVO").value(0));
    }

    @Test
    @DisplayName("Entregar de más solo vale en efectivo, y nunca por debajo del importe")
    void vueltoSoloEnEfectivo() throws Exception {
        String caja = cajaAbierta("0");
        contar(CEMENTO, "10");

        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                        {"cajaId": "%s", "serieId": "%s", "lineas": [{"productoId": "%s", "cantidad": 1}],
                         "pagos": [{"forma": "TARJETA", "monto": 32.50, "entregado": 40.00}]}
                        """.formatted(caja, SERIE_NV, CEMENTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("vuelto_solo_en_efectivo"));

        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                        {"cajaId": "%s", "serieId": "%s", "lineas": [{"productoId": "%s", "cantidad": 1}],
                         "pagos": [{"forma": "EFECTIVO", "monto": 32.50, "entregado": 20.00}]}
                        """.formatted(caja, SERIE_NV, CEMENTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("entregado_insuficiente"));
    }

    @Test
    @DisplayName("El catálogo del mostrador trae precio y existencias del local, y las series para elegir")
    void catalogoDelMostrador() throws Exception {
        contar(CEMENTO, "25");

        mockMvc.perform(comoAdministrador(get("/api/v1/almacen/productos/disponibles"))
                        .param("sucursalId", MATRIZ).param("q", "cemento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("CEM-001"))
                .andExpect(jsonPath("$[0].precio").value(32.5))
                .andExpect(jsonPath("$[0].existencia").value(25));

        // Un servicio no tiene existencias: null, no cero.
        mockMvc.perform(comoAdministrador(get("/api/v1/almacen/productos/disponibles"))
                        .param("sucursalId", MATRIZ).param("q", "instalacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].controlaStock").value(false))
                .andExpect(jsonPath("$[0].existencia").doesNotExist());

        mockMvc.perform(comoAdministrador(get("/api/v1/ventas/series"))
                        .param("tipo", "NOTA_VENTA").param("sucursalId", MATRIZ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serie").value("N001"));
    }

    @Test
    @DisplayName("Un documento emitido es inmutable en la base, salvo su estado")
    void inmutable() throws Exception {
        String caja = cajaAbierta("0");
        String id = leer(mockMvc.perform(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                        {"cajaId": "%s", "serieId": "%s", "lineas": [{"productoId": "%s", "cantidad": 1}],
                         "pagos": [{"forma": "EFECTIVO", "monto": 80.00}]}
                        """.formatted(caja, SERIE_NV, INSTALACION)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("documento").path("id").asString();

        com.ondexia.infrastructure.seguridad.ContextoDePrueba.comoUsuarioDe(
                java.util.UUID.fromString(USUARIO_DEMO), CUENTA_DEMO,
                java.util.UUID.fromString(EMPRESA_ADMINISTRADA));
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    transaccion.executeWithoutResult(estado ->
                            jdbc.sql("update documento_venta set total = 1 where id = ?::uuid")
                                    .param(id).update()))
                    .hasMessageContaining("no se modifica");
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    transaccion.executeWithoutResult(estado ->
                            jdbc.sql("delete from documento_venta where id = ?::uuid").param(id).update()))
                    .hasMessageContaining("no se borra");
            // El estado sí: es lo único que cambia después.
            transaccion.executeWithoutResult(estado ->
                    jdbc.sql("update documento_venta set estado = 'ANULADO' where id = ?::uuid").param(id).update());
        } finally {
            com.ondexia.infrastructure.seguridad.ContextoDePrueba.limpiar();
        }
    }

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transaccion;
}
