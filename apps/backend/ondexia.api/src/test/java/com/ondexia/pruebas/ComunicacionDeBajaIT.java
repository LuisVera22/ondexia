package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.application.emision.ComunicacionesDeBaja;
import com.ondexia.domain.comprobante.ClavesDelBus;
import com.ondexia.domain.comprobante.EstadoSunat;
import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.comprobante.ResultadoDeEmision;
import com.ondexia.infrastructure.salida.emision.BusDeEmisionEnMemoria;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
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
 * La comunicación de baja de extremo a extremo (doc 13 §6), con el bus en
 * memoria haciendo de S3 y esta prueba haciendo de SUNAT: el envío que devuelve
 * ticket, la consulta que lo resuelve, y la factura que queda anulada solo
 * cuando SUNAT acepta.
 */
class ComunicacionDeBajaIT extends PruebaIntegracion {

    private static final String MATRIZ = "00000000-0000-4000-8000-000000000020";
    private static final String INSTALACION = "00000000-0000-4000-8000-000000000062";
    private static final String CLIENTE_RUC = "00000000-0000-4000-8000-000000000081";
    private static final String CLIENTE_DNI = "00000000-0000-4000-8000-000000000080";
    private static final String SERIE_FACTURA = "00000000-0000-4000-8000-0000000000a2";
    private static final String SERIE_BOLETA = "00000000-0000-4000-8000-0000000000a1";
    private static final String BAJAS = "/api/v1/ventas/comunicaciones-de-baja";

    private static final AtomicInteger CORRELATIVO_CAJAS = new AtomicInteger(800);

    @Autowired
    private ObjectMapper json;

    @Autowired
    private BusDeEmisionEnMemoria bus;

    @Autowired
    private ComunicacionesDeBaja comunicaciones;

    private MockHttpServletRequestBuilder comoAdministrador(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", autorizacionDemo())
                .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode pedir(MockHttpServletRequestBuilder peticion, int esperado) throws Exception {
        return json.readTree(mockMvc.perform(peticion)
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String cajaAbierta() throws Exception {
        String caja = pedir(comoAdministrador(post("/api/v1/ventas/cajas")).content("""
                {"codigo": "BAJ%d", "nombre": "Caja de baja", "sucursalId": "%s"}
                """.formatted(CORRELATIVO_CAJAS.incrementAndGet(), MATRIZ)), 201)
                .path("id").asString();
        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/cajas/" + caja + "/sesiones"))
                        .content("{\"montoInicial\": 0}"))
                .andExpect(status().isCreated());
        return caja;
    }

    private void aceptarEnSunat(String documentoId) throws Exception {
        JsonNode estado = pedir(comoAdministrador(
                get("/api/v1/ventas/comprobantes/" + documentoId + "/sunat")), 200);
        UUID comprobanteId = UUID.fromString(estado.path("comprobanteId").asString());
        OrdenDeEmision orden = bus.ordenes().stream().filter(o -> o.id().equals(comprobanteId))
                .reduce((a, b) -> b).orElseThrow(() -> new AssertionError("sin orden"));
        String nombre = orden.documento().nombreDeArchivo(orden.emisor().ruc());
        bus.depositarResultado(new ResultadoDeEmision(orden.id(), orden.empresaId(),
                OrdenDeEmision.Operacion.EMITIR, EstadoSunat.ACEPTADO, "0", "Aceptada", List.of(),
                ClavesDelBus.xml("20100000009", nombre), ClavesDelBus.cdr("20100000009", nombre),
                "hash=", null, null, Instant.now()));
        mockMvc.perform(comoAdministrador(get("/api/v1/ventas/comprobantes/" + documentoId + "/sunat")))
                .andExpect(jsonPath("$.estado").value("ACEPTADO"));
    }

    /** Una factura de S/ 80 al cliente con RUC, ya aceptada por SUNAT. */
    private JsonNode facturaAceptada() throws Exception {
        JsonNode documento = pedir(comoAdministrador(post("/api/v1/ventas/comprobantes")).content("""
                {"tipo": "FACTURA", "venta": {"cajaId": "%s", "serieId": "%s", "clienteId": "%s",
                 "lineas": [{"productoId": "%s", "cantidad": 1}],
                 "pagos": [{"forma": "EFECTIVO", "monto": 80.00}]}}
                """.formatted(cajaAbierta(), SERIE_FACTURA, CLIENTE_RUC, INSTALACION)), 201)
                .path("documento");
        aceptarEnSunat(documento.path("id").asString());
        return documento;
    }

    /** Hace de SUNAT recibiendo el archivo: devuelve ticket. */
    private void darTicket(UUID comunicacionId, String ticket) {
        OrdenDeEmision orden = bus.ordenes().stream().filter(o -> o.id().equals(comunicacionId))
                .reduce((a, b) -> b).orElseThrow(() -> new AssertionError("no se publicó el envío"));
        assertThat(orden.operacion()).isEqualTo(OrdenDeEmision.Operacion.ENVIAR_BAJA);
        bus.depositarResultado(new ResultadoDeEmision(orden.id(), orden.empresaId(),
                OrdenDeEmision.Operacion.ENVIAR_BAJA, EstadoSunat.EN_PROCESO, ticket,
                "SUNAT recibió el envío", List.of(),
                ClavesDelBus.DOCUMENTOS + "20100000009/20100000009-" + orden.baja().identificador() + ".xml",
                null, "hash=", null, null, Instant.now()));
    }

    /**
     * Y haciendo de SUNAT respondiendo al ticket.
     *
     * <p>Lee la comunicación por el caso de uso y no por HTTP porque necesita el
     * identificador con el que viaja la consulta, que no se expone. Eso obliga a
     * poner el contexto a mano: fuera de una petición no hay empresa activa, y
     * sin ella la política de aislamiento no deja ver ninguna fila.
     */
    private void responderTicket(UUID comunicacionId, EstadoSunat estado, String codigo,
            String descripcion) {
        ContextoDePrueba.comoUsuarioDe(UUID.fromString(USUARIO_DEMO), CUENTA_DEMO,
                UUID.fromString(EMPRESA_ADMINISTRADA));
        try {
            var comunicacion = comunicaciones.obtener(comunicacionId);
            bus.depositarResultado(new ResultadoDeEmision(
                    ComunicacionesDeBaja.idDeLaConsulta(comunicacion), comunicacion.empresaId(),
                    OrdenDeEmision.Operacion.CONSULTAR_TICKET, estado, codigo, descripcion, List.of(),
                    null, estado == EstadoSunat.ACEPTADO
                            ? ClavesDelBus.cdrDeTicket("20100000009", comunicacion.ticket()) : null,
                    null, null, null, Instant.now()));
        } finally {
            ContextoDePrueba.limpiar();
        }
    }

    @Test
    @DisplayName("Baja completa: envío con ticket, consulta que resuelve, y la factura queda anulada")
    void bajaCompleta() throws Exception {
        JsonNode factura = facturaAceptada();
        String facturaId = factura.path("id").asString();

        JsonNode baja = pedir(comoAdministrador(post(BAJAS)).content("""
                {"comprobantes": [{"documentoId": "%s", "motivo": "Emitida al cliente equivocado"}]}
                """.formatted(facturaId)), 201);

        assertThat(baja.path("estado").asString()).isEqualTo("EN_COLA");
        assertThat(baja.path("identificador").asString()).startsWith("RA-");
        assertThat(baja.path("comprobantes").size()).isEqualTo(1);
        assertThat(baja.path("comprobantes").get(0).path("numeroCompleto").asString())
                .isEqualTo(factura.path("numeroCompleto").asString());
        assertThat(baja.path("diasDePlazo").asLong()).isGreaterThan(0);
        UUID bajaId = UUID.fromString(baja.path("id").asString());

        // La orden que salió lleva lo que el XML necesita.
        OrdenDeEmision orden = bus.ordenes().stream().filter(o -> o.id().equals(bajaId))
                .findFirst().orElseThrow(() -> new AssertionError("no se publicó el envío"));
        assertThat(orden.operacion()).isEqualTo(OrdenDeEmision.Operacion.ENVIAR_BAJA);
        assertThat(orden.baja().comprobantes()).hasSize(1);
        assertThat(orden.baja().comprobantes().get(0).tipo()).isEqualTo("01");
        assertThat(orden.baja().comprobantes().get(0).motivo()).isEqualTo("Emitida al cliente equivocado");
        assertThat(orden.baja().fechaDeLosComprobantes().toString())
                .isEqualTo(factura.path("fechaEmision").asString());

        // SUNAT lo recibe y da un ticket: la consulta del listado lo aplica.
        darTicket(bajaId, "1554895");
        JsonNode enProceso = pedir(comoAdministrador(get(BAJAS + "/" + bajaId)), 200);
        assertThat(enProceso.path("estado").asString()).isEqualTo("EN_PROCESO");
        assertThat(enProceso.path("ticket").asString()).isEqualTo("1554895");
        assertThat(enProceso.path("xmlDisponible").asBoolean()).isTrue();
        assertThat(enProceso.path("admiteReintento").asBoolean()).isFalse();

        // Mientras tanto la factura sigue vigente.
        assertThat(pedir(comoAdministrador(get("/api/v1/ventas/comprobantes/" + facturaId)), 200)
                .path("estado").asString()).isEqualTo("EMITIDO");

        // Y con el veredicto, deja de existir.
        responderTicket(bajaId, EstadoSunat.ACEPTADO, "0", "La comunicación de baja fue aceptada");
        JsonNode aceptada = pedir(comoAdministrador(get(BAJAS + "/" + bajaId)), 200);
        assertThat(aceptada.path("estado").asString()).isEqualTo("ACEPTADO");
        assertThat(aceptada.path("codigo").asString()).isEqualTo("0");
        assertThat(aceptada.path("cdrDisponible").asBoolean()).isTrue();

        assertThat(pedir(comoAdministrador(get("/api/v1/ventas/comprobantes/" + facturaId)), 200)
                .path("estado").asString()).isEqualTo("ANULADO");

        // Y no se da de baja dos veces.
        assertThat(pedir(comoAdministrador(post(BAJAS)).content("""
                {"comprobantes": [{"documentoId": "%s", "motivo": "Otra vez"}]}
                """.formatted(facturaId)), 400).path("codigo").asString())
                .isEqualTo("documento_no_anulable");
    }

    @Test
    @DisplayName("Rechazada por SUNAT: se ve el código y se puede volver a enviar")
    void rechazadaYReintento() throws Exception {
        String facturaId = facturaAceptada().path("id").asString();
        UUID bajaId = UUID.fromString(pedir(comoAdministrador(post(BAJAS)).content("""
                {"comprobantes": [{"documentoId": "%s", "motivo": "Duplicada"}]}
                """.formatted(facturaId)), 201).path("id").asString());

        darTicket(bajaId, "1554896");
        pedir(comoAdministrador(get(BAJAS + "/" + bajaId)), 200);
        responderTicket(bajaId, EstadoSunat.RECHAZADO, "2324",
                "El comprobante no existe o no está autorizado");

        JsonNode rechazada = pedir(comoAdministrador(get(BAJAS + "/" + bajaId)), 200);
        assertThat(rechazada.path("estado").asString()).isEqualTo("RECHAZADO");
        assertThat(rechazada.path("codigo").asString()).isEqualTo("2324");
        assertThat(rechazada.path("admiteReintento").asBoolean()).isTrue();
        // La factura no se anuló: SUNAT no aceptó la baja.
        assertThat(pedir(comoAdministrador(get("/api/v1/ventas/comprobantes/" + facturaId)), 200)
                .path("estado").asString()).isEqualTo("EMITIDO");

        int ordenesAntes = bus.ordenes().size();
        JsonNode reintentada = pedir(comoAdministrador(post(BAJAS + "/" + bajaId + "/reintento")), 200);
        assertThat(reintentada.path("estado").asString()).isEqualTo("EN_COLA");
        assertThat(reintentada.path("intentos").asInt()).isEqualTo(2);
        assertThat(reintentada.path("ticket").isNull()).isTrue();
        assertThat(bus.ordenes()).hasSize(ordenesAntes + 1);
    }

    @Test
    @DisplayName("Una boleta no se da de baja por esta vía, y dos días distintos tampoco van juntos")
    void loQueNoSeAdmite() throws Exception {
        String caja = cajaAbierta();
        String boletaId = pedir(comoAdministrador(post("/api/v1/ventas/comprobantes")).content("""
                {"tipo": "BOLETA", "venta": {"cajaId": "%s", "serieId": "%s", "clienteId": "%s",
                 "lineas": [{"productoId": "%s", "cantidad": 1}],
                 "pagos": [{"forma": "EFECTIVO", "monto": 80.00}]}}
                """.formatted(caja, SERIE_BOLETA, CLIENTE_DNI, INSTALACION)), 201)
                .path("documento").path("id").asString();
        aceptarEnSunat(boletaId);

        assertThat(pedir(comoAdministrador(post(BAJAS)).content("""
                {"comprobantes": [{"documentoId": "%s", "motivo": "Error"}]}
                """.formatted(boletaId)), 400).path("codigo").asString())
                .isEqualTo("tipo_no_dado_de_baja");

        // Sin comprobantes tampoco.
        mockMvc.perform(comoAdministrador(post(BAJAS)).content("{\"comprobantes\": []}"))
                .andExpect(status().isBadRequest());

        // Ni sin motivo.
        mockMvc.perform(comoAdministrador(post(BAJAS)).content("""
                {"comprobantes": [{"documentoId": "%s", "motivo": ""}]}
                """.formatted(boletaId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un comprobante que SUNAT no aceptó no se comunica de baja")
    void comprobantePendiente() throws Exception {
        String pendienteId = pedir(comoAdministrador(post("/api/v1/ventas/comprobantes")).content("""
                {"tipo": "FACTURA", "venta": {"cajaId": "%s", "serieId": "%s", "clienteId": "%s",
                 "lineas": [{"productoId": "%s", "cantidad": 1}],
                 "pagos": [{"forma": "EFECTIVO", "monto": 80.00}]}}
                """.formatted(cajaAbierta(), SERIE_FACTURA, CLIENTE_RUC, INSTALACION)), 201)
                .path("documento").path("id").asString();

        assertThat(pedir(comoAdministrador(post(BAJAS)).content("""
                {"comprobantes": [{"documentoId": "%s", "motivo": "Error"}]}
                """.formatted(pendienteId)), 400).path("codigo").asString())
                .isEqualTo("documento_no_anulable");
    }

    @Test
    @DisplayName("El listado trae las comunicaciones y sincroniza lo que esté en curso")
    void listado() throws Exception {
        String facturaId = facturaAceptada().path("id").asString();
        UUID bajaId = UUID.fromString(pedir(comoAdministrador(post(BAJAS)).content("""
                {"comprobantes": [{"documentoId": "%s", "motivo": "Cliente equivocado"}]}
                """.formatted(facturaId)), 201).path("id").asString());
        darTicket(bajaId, "1554897");

        JsonNode listado = pedir(comoAdministrador(get(BAJAS)), 200);

        boolean encontrada = false;
        for (JsonNode item : listado) {
            if (bajaId.toString().equals(item.path("id").asString())) {
                encontrada = true;
                assertThat(item.path("estado").asString())
                        .as("el listado sincroniza antes de responder").isEqualTo("EN_PROCESO");
                assertThat(item.path("ticket").asString()).isEqualTo("1554897");
            }
        }
        assertThat(encontrada).isTrue();
    }
}
