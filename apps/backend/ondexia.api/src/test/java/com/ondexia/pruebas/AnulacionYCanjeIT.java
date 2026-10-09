package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.domain.comprobante.ClavesDelBus;
import com.ondexia.domain.comprobante.EstadoSunat;
import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.comprobante.ResultadoDeEmision;
import com.ondexia.infrastructure.salida.emision.BusDeEmisionEnMemoria;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Anular con nota de crédito y canjear una nota de venta (doc 13 §5), de
 * extremo a extremo: lo que se emite, lo que vuelve al almacén, lo que el
 * arqueo cuenta y en qué momento cambia el estado del original.
 */
class AnulacionYCanjeIT extends PruebaIntegracion {

    private static final String MATRIZ = "00000000-0000-4000-8000-000000000020";
    private static final String ALMACEN_MATRIZ = "00000000-0000-4000-8000-000000000090";
    private static final String CEMENTO = "00000000-0000-4000-8000-000000000060";
    private static final String CLIENTE_DNI = "00000000-0000-4000-8000-000000000080";
    private static final String SERIE_NV = "00000000-0000-4000-8000-0000000000a0";
    private static final String SERIE_BOLETA = "00000000-0000-4000-8000-0000000000a1";
    private static final String SERIE_NOTA_B = "00000000-0000-4000-8000-0000000000a4";

    private static final AtomicInteger CORRELATIVO_CAJAS = new AtomicInteger(700);

    @Autowired
    private ObjectMapper json;

    @Autowired
    private BusDeEmisionEnMemoria bus;

    private MockHttpServletRequestBuilder comoAdministrador(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", autorizacionDemo())
                .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode leer(String cuerpo) {
        return json.readTree(cuerpo);
    }

    private JsonNode pedir(MockHttpServletRequestBuilder peticion, int esperado) throws Exception {
        return leer(mockMvc.perform(peticion)
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String cajaAbierta() throws Exception {
        String caja = pedir(comoAdministrador(post("/api/v1/ventas/cajas")).content("""
                {"codigo": "ANU%d", "nombre": "Caja de anulación", "sucursalId": "%s"}
                """.formatted(CORRELATIVO_CAJAS.incrementAndGet(), MATRIZ)), 201)
                .path("id").asString();
        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/cajas/" + caja + "/sesiones"))
                        .content("{\"montoInicial\": 0}"))
                .andExpect(status().isCreated());
        return caja;
    }

    /**
     * Cierra la sesión y devuelve el arqueo. El calculado solo existe al cerrar:
     * mientras la caja está abierta la respuesta lo trae vacío.
     */
    private JsonNode cerrarYArquear(String caja, String declarado) throws Exception {
        String sesion = pedir(comoAdministrador(
                get("/api/v1/ventas/cajas/" + caja + "/sesion-abierta")), 200).path("id").asString();
        return pedir(comoAdministrador(put("/api/v1/ventas/cajas/sesiones/" + sesion + "/cierre"))
                .content("{\"declarado\": " + declarado + "}"), 200);
    }

    private void contar(String producto, String cantidad) throws Exception {
        mockMvc.perform(comoAdministrador(post("/api/v1/almacen/productos/" + producto + "/existencias/ajustes"))
                        .content("{\"almacenId\": \"%s\", \"cantidad\": %s, \"motivo\": \"Conteo\"}"
                                .formatted(ALMACEN_MATRIZ, cantidad)))
                .andExpect(status().isOk());
    }

    private String existencia(String producto) throws Exception {
        JsonNode existencias = pedir(comoAdministrador(
                get("/api/v1/almacen/productos/" + producto + "/existencias")), 200);
        for (JsonNode e : existencias) {
            if (ALMACEN_MATRIZ.equals(e.path("almacenId").asString())) {
                return e.path("cantidad").decimalValue().stripTrailingZeros().toPlainString();
            }
        }
        return "0";
    }

    /** Hace de SUNAT: acepta lo que esté en cola para ese documento. */
    private void aceptarEnSunat(String documentoId) throws Exception {
        JsonNode estado = pedir(comoAdministrador(
                get("/api/v1/ventas/comprobantes/" + documentoId + "/sunat")), 200);
        UUID comprobanteId = UUID.fromString(estado.path("comprobanteId").asString());
        OrdenDeEmision orden = bus.ordenes().stream()
                .filter(o -> o.id().equals(comprobanteId))
                .reduce((primera, ultima) -> ultima)
                .orElseThrow(() -> new AssertionError("no se publicó la orden del documento"));
        String nombre = orden.documento().nombreDeArchivo(orden.emisor().ruc());
        bus.depositarResultado(new ResultadoDeEmision(orden.id(), orden.empresaId(),
                OrdenDeEmision.Operacion.EMITIR, EstadoSunat.ACEPTADO, "0", "Aceptada", List.of(),
                ClavesDelBus.xml("20100000009", nombre), ClavesDelBus.cdr("20100000009", nombre),
                "hash=", null, null, Instant.now()));
        // La consulta es la que aplica el resultado.
        mockMvc.perform(comoAdministrador(get("/api/v1/ventas/comprobantes/" + documentoId + "/sunat")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACEPTADO"));
    }

    /** Una boleta de dos bolsas de cemento (65.00) a nombre del cliente con DNI, ya aceptada. */
    private JsonNode boletaAceptada(String caja) throws Exception {
        JsonNode documento = pedir(comoAdministrador(post("/api/v1/ventas/comprobantes")).content("""
                {"tipo": "BOLETA", "venta": {"cajaId": "%s", "serieId": "%s", "clienteId": "%s",
                 "lineas": [{"productoId": "%s", "cantidad": 2}],
                 "pagos": [{"forma": "EFECTIVO", "monto": 65.00}]}}
                """.formatted(caja, SERIE_BOLETA, CLIENTE_DNI, CEMENTO)), 201).path("documento");
        aceptarEnSunat(documento.path("id").asString());
        return documento;
    }

    @Test
    @DisplayName("Anular: la nota copia el comprobante, repone existencias y el arqueo resta la devolución")
    void anulacionCompleta() throws Exception {
        String caja = cajaAbierta();
        contar(CEMENTO, "10");
        JsonNode boleta = boletaAceptada(caja);
        String boletaId = boleta.path("id").asString();
        assertThat(existencia(CEMENTO)).isEqualTo("8");

        JsonNode nota = pedir(comoAdministrador(
                post("/api/v1/ventas/comprobantes/" + boletaId + "/anulacion")).content("""
                {"cajaId": "%s", "serieId": "%s",
                 "pagos": [{"forma": "EFECTIVO", "monto": 65.00}],
                 "observaciones": "El cliente devolvió la mercadería"}
                """.formatted(caja, SERIE_NOTA_B)), 201);

        assertThat(nota.path("tipo").asString()).isEqualTo("07");
        assertThat(nota.path("fiscal").asBoolean()).isTrue();
        assertThat(nota.path("estado").asString()).isEqualTo("PENDIENTE");
        assertThat(nota.path("numeroCompleto").asString()).startsWith("BC01-");
        assertThat(nota.path("motivo").asString()).isEqualTo("ANULACION_DE_LA_OPERACION");
        assertThat(nota.path("origen").path("numeroCompleto").asString())
                .isEqualTo(boleta.path("numeroCompleto").asString());
        assertThat(nota.path("total").decimalValue()).isEqualByComparingTo("65.00");
        assertThat(nota.path("lineas").size()).isEqualTo(1);

        // La mercadería volvió al almacén del local.
        assertThat(existencia(CEMENTO)).isEqualTo("10");

        // Mientras la nota está en camino, la boleta sigue vigente: si SUNAT la
        // rechazara, la anulación no habría ocurrido.
        assertThat(pedir(comoAdministrador(get("/api/v1/ventas/comprobantes/" + boletaId)), 200)
                .path("estado").asString()).isEqualTo("EMITIDO");

        // Ni dos anulaciones a la vez...
        pedir(comoAdministrador(post("/api/v1/ventas/comprobantes/" + boletaId + "/anulacion"))
                .content("{\"cajaId\": \"" + caja + "\", \"serieId\": \"" + SERIE_NOTA_B + "\"}"), 400)
                .path("codigo").asString();

        // ...ni otra después de que SUNAT acepte la primera.
        aceptarEnSunat(nota.path("id").asString());
        assertThat(pedir(comoAdministrador(get("/api/v1/ventas/comprobantes/" + boletaId)), 200)
                .path("estado").asString()).isEqualTo("ANULADO");

        assertThat(pedir(comoAdministrador(post("/api/v1/ventas/comprobantes/" + boletaId + "/anulacion"))
                .content("{\"cajaId\": \"" + caja + "\", \"serieId\": \"" + SERIE_NOTA_B + "\"}"), 400)
                .path("codigo").asString()).isEqualTo("documento_ya_anulado");

        // El arqueo cuenta la venta y la devolución, las dos en esta sesión: se
        // cobraron 65 y se devolvieron 65, así que en el cajón no queda nada.
        JsonNode arqueo = cerrarYArquear(caja, "{\"EFECTIVO\": 0}");
        assertThat(arqueo.path("calculado").path("EFECTIVO").decimalValue())
                .isEqualByComparingTo("0.00");
        assertThat(arqueo.path("diferencia").path("EFECTIVO").decimalValue())
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("La orden que sale hacia SUNAT lleva el motivo y el comprobante que modifica")
    void laOrdenDeLaNotaLlevaLaReferencia() throws Exception {
        String caja = cajaAbierta();
        contar(CEMENTO, "10");
        JsonNode boleta = boletaAceptada(caja);

        JsonNode nota = pedir(comoAdministrador(
                post("/api/v1/ventas/comprobantes/" + boleta.path("id").asString() + "/anulacion"))
                .content("{\"cajaId\": \"" + caja + "\", \"serieId\": \"" + SERIE_NOTA_B + "\"}"), 201);

        UUID comprobanteId = UUID.fromString(pedir(comoAdministrador(
                get("/api/v1/ventas/comprobantes/" + nota.path("id").asString() + "/sunat")), 200)
                .path("comprobanteId").asString());
        OrdenDeEmision orden = bus.ordenes().stream().filter(o -> o.id().equals(comprobanteId))
                .findFirst().orElseThrow(() -> new AssertionError("no se publicó la orden"));

        assertThat(orden.documento().tipo()).isEqualTo("07");
        assertThat(orden.documento().esNotaDeCredito()).isTrue();
        assertThat(orden.documento().motivoNota()).isEqualTo("01");
        assertThat(orden.documento().referencia().tipo()).isEqualTo("03");
        assertThat(orden.documento().referencia().numeroCompleto())
                .isEqualTo("B001-" + boleta.path("numero").asLong());
        assertThat(orden.documento().total()).isEqualByComparingTo("65.00");
    }

    @Test
    @DisplayName("Una devolución por ítem acredita una parte y deja la boleta vigente")
    void devolucionPorItem() throws Exception {
        String caja = cajaAbierta();
        contar(CEMENTO, "10");
        JsonNode boleta = boletaAceptada(caja);

        JsonNode nota = pedir(comoAdministrador(post("/api/v1/ventas/notas-de-credito")).content("""
                {"documentoId": "%s", "motivo": "DEVOLUCION_POR_ITEM", "cajaId": "%s",
                 "serieId": "%s", "lineas": [{"orden": 1, "cantidad": 1}],
                 "pagos": [{"forma": "EFECTIVO", "monto": 32.50}]}
                """.formatted(boleta.path("id").asString(), caja, SERIE_NOTA_B)), 201);

        assertThat(nota.path("total").decimalValue()).isEqualByComparingTo("32.50");
        assertThat(nota.path("motivo").asString()).isEqualTo("DEVOLUCION_POR_ITEM");
        assertThat(existencia(CEMENTO)).as("vuelve una bolsa").isEqualTo("9");

        aceptarEnSunat(nota.path("id").asString());
        assertThat(pedir(comoAdministrador(
                get("/api/v1/ventas/comprobantes/" + boleta.path("id").asString())), 200)
                .path("estado").asString())
                .as("una devolución parcial no anula el comprobante").isEqualTo("EMITIDO");

        // Y aparece entre los documentos que salieron de la boleta.
        JsonNode relacionados = pedir(comoAdministrador(get("/api/v1/ventas/documentos/"
                + boleta.path("id").asString() + "/relacionados")), 200);
        assertThat(relacionados.size()).isEqualTo(1);
        assertThat(relacionados.get(0).path("tipo").asString()).isEqualTo("07");
    }

    @Test
    @DisplayName("Anular por la puerta de las correcciones, y al revés, se rechaza")
    void cadaMotivoPorSuPuerta() throws Exception {
        String caja = cajaAbierta();
        contar(CEMENTO, "10");
        JsonNode boleta = boletaAceptada(caja);
        String boletaId = boleta.path("id").asString();

        // Un motivo que anula, por la ruta de las correcciones.
        assertThat(pedir(comoAdministrador(post("/api/v1/ventas/notas-de-credito")).content("""
                {"documentoId": "%s", "motivo": "ANULACION_DE_LA_OPERACION", "cajaId": "%s",
                 "serieId": "%s"}
                """.formatted(boletaId, caja, SERIE_NOTA_B)), 400)
                .path("codigo").asString()).isEqualTo("motivo_anula");

        // Y uno que no anula, por la de anulación.
        assertThat(pedir(comoAdministrador(post("/api/v1/ventas/comprobantes/" + boletaId + "/anulacion"))
                .content("""
                {"motivo": "DESCUENTO_GLOBAL", "cajaId": "%s", "serieId": "%s"}
                """.formatted(caja, SERIE_NOTA_B)), 400)
                .path("codigo").asString()).isEqualTo("motivo_no_anula");
    }

    @Test
    @DisplayName("Canjear: la boleta hereda las líneas, la nota de venta queda canjeada y el arqueo no cambia")
    void canje() throws Exception {
        String caja = cajaAbierta();
        contar(CEMENTO, "10");

        JsonNode notaDeVenta = pedir(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                {"cajaId": "%s", "serieId": "%s", "lineas": [{"productoId": "%s", "cantidad": 2}],
                 "pagos": [{"forma": "EFECTIVO", "monto": 65.00}]}
                """.formatted(caja, SERIE_NV, CEMENTO)), 201).path("documento");
        String notaId = notaDeVenta.path("id").asString();
        assertThat(existencia(CEMENTO)).isEqualTo("8");

        JsonNode boleta = pedir(comoAdministrador(
                post("/api/v1/ventas/notas-de-venta/" + notaId + "/canje")).content("""
                {"tipo": "BOLETA", "serieId": "%s", "clienteId": "%s"}
                """.formatted(SERIE_BOLETA, CLIENTE_DNI)), 201);

        assertThat(boleta.path("tipo").asString()).isEqualTo("03");
        assertThat(boleta.path("estado").asString()).isEqualTo("PENDIENTE");
        assertThat(boleta.path("total").decimalValue()).isEqualByComparingTo("65.00");
        assertThat(boleta.path("lineas").size()).isEqualTo(1);
        assertThat(boleta.path("cliente").path("numeroDocumento").asString()).isEqualTo("70123456");
        assertThat(boleta.path("origen").path("numeroCompleto").asString())
                .isEqualTo(notaDeVenta.path("numeroCompleto").asString());
        assertThat(boleta.path("pagos").size()).as("el dinero ya entró con la nota de venta").isZero();

        assertThat(pedir(comoAdministrador(get("/api/v1/ventas/notas-de-venta/" + notaId)), 200)
                .path("estado").asString()).isEqualTo("CANJEADO");

        // Y no se canjea dos veces.
        assertThat(pedir(comoAdministrador(post("/api/v1/ventas/notas-de-venta/" + notaId + "/canje"))
                .content("{\"tipo\": \"BOLETA\", \"serieId\": \"" + SERIE_BOLETA + "\"}"), 400)
                .path("codigo").asString()).isEqualTo("nota_de_venta_no_canjeable");

        // Ni se descarga otra vez la mercadería, ni se cuenta otra vez el
        // dinero: el arqueo ve los 65 de la nota de venta y nada más.
        assertThat(existencia(CEMENTO)).isEqualTo("8");
        JsonNode arqueo = cerrarYArquear(caja, "{\"EFECTIVO\": 65.00}");
        assertThat(arqueo.path("calculado").path("EFECTIVO").decimalValue())
                .isEqualByComparingTo("65.00");
    }

    @Test
    @DisplayName("El catálogo de motivos dice cuál anula y cuál repone existencias")
    void catalogoDeMotivos() throws Exception {
        JsonNode motivos = pedir(comoAdministrador(get("/api/v1/ventas/notas-de-credito/motivos")), 200);

        assertThat(motivos.size()).isEqualTo(10);
        JsonNode anulacion = null;
        JsonNode descuento = null;
        for (JsonNode motivo : motivos) {
            if ("ANULACION_DE_LA_OPERACION".equals(motivo.path("codigo").asString())) {
                anulacion = motivo;
            }
            if ("DESCUENTO_GLOBAL".equals(motivo.path("codigo").asString())) {
                descuento = motivo;
            }
        }
        assertThat(anulacion).isNotNull();
        assertThat(anulacion.path("codigoSunat").asString()).isEqualTo("01");
        assertThat(anulacion.path("anula").asBoolean()).isTrue();
        assertThat(anulacion.path("repone").asBoolean()).isTrue();
        assertThat(descuento.path("anula").asBoolean()).isFalse();
        assertThat(descuento.path("repone").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("La nota de crédito no se puede desactivar: anular tiene que ser posible siempre")
    void laNotaDeCreditoNoSeApaga() throws Exception {
        assertThat(pedir(comoAdministrador(put("/api/v1/configuracion/comprobantes/07"))
                .content("{\"emite\": false}"), 400)
                .path("codigo").asString()).isEqualTo("tipo_no_desactivable");
    }
}
