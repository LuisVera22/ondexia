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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
 * La emisión electrónica desde la API (doc 14 §2–§4), con el bus en memoria
 * haciendo de S3 y esta prueba haciendo de Emisor: lo que la API publica, lo que
 * aplica cuando hay resultado y lo que deja hacer con el certificado.
 */
class EmisionElectronicaIT extends PruebaIntegracion {

    private static final String MATRIZ = "00000000-0000-4000-8000-000000000020";
    private static final String INSTALACION = "00000000-0000-4000-8000-000000000062";
    private static final String CLIENTE_DNI = "00000000-0000-4000-8000-000000000080";
    private static final String CLIENTE_RUC = "00000000-0000-4000-8000-000000000081";
    private static final String SERIE_BOLETA = "00000000-0000-4000-8000-0000000000a1";
    private static final String SERIE_FACTURA = "00000000-0000-4000-8000-0000000000a2";
    private static final String RUC_DEMO = "20100000009";

    private static final AtomicInteger CORRELATIVO_CAJAS = new AtomicInteger(500);

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private BusDeEmisionEnMemoria bus;

    @AfterEach
    void restaurarCredenciales() {
        // Lo que la V900 deja: certificado cargado y verificado, usuario SOL de la beta.
        jdbc.sql("""
                update empresa set usuario_sol = 'MODDATOS', modo_sunat = 'BETA',
                       certificado_cargado_en = now(), certificado_verificado_en = now(),
                       certificado_sujeto = 'CN=COMERCIAL DEMO S.A.C. (certificado de ejemplo)',
                       certificado_vence_en = date '2030-12-31', certificado_error = null
                 where id = ?::uuid""").param(EMPRESA_ADMINISTRADA).update();
    }

    private MockHttpServletRequestBuilder comoAdministrador(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", autorizacionDemo())
                .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode leer(String cuerpo) {
        return json.readTree(cuerpo);
    }

    private String cajaAbierta() throws Exception {
        String caja = leer(mockMvc.perform(comoAdministrador(post("/api/v1/ventas/cajas")).content("""
                        {"codigo": "EMI%d", "nombre": "Caja de emisión", "sucursalId": "%s"}
                        """.formatted(CORRELATIVO_CAJAS.incrementAndGet(), MATRIZ)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("id").asString();
        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/cajas/" + caja + "/sesiones"))
                        .content("{\"montoInicial\": 0}"))
                .andExpect(status().isCreated());
        return caja;
    }

    /** Una instalación de S/ 80 al cliente indicado. */
    private String cuerpo(String tipo, String caja, String serie, String clienteId) {
        return """
                {"tipo": "%s", "venta": {"cajaId": "%s", "serieId": "%s", "clienteId": "%s",
                 "lineas": [{"productoId": "%s", "cantidad": 1}],
                 "pagos": [{"forma": "EFECTIVO", "monto": 80.00}]}}
                """.formatted(tipo, caja, serie, clienteId, INSTALACION);
    }

    private JsonNode emitir(String tipo, String serie, String clienteId) throws Exception {
        return leer(mockMvc.perform(comoAdministrador(post("/api/v1/ventas/comprobantes"))
                        .content(cuerpo(tipo, cajaAbierta(), serie, clienteId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("documento");
    }

    private OrdenDeEmision ordenDe(UUID comprobanteId) {
        return bus.ordenes().stream().filter(o -> o.id().equals(comprobanteId)).findFirst()
                .orElseThrow(() -> new AssertionError("la orden no se publicó"));
    }

    private JsonNode sunat(String documentoId) throws Exception {
        return leer(mockMvc.perform(comoAdministrador(get("/api/v1/ventas/comprobantes/" + documentoId + "/sunat")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultadoDeEmision resultado(OrdenDeEmision orden, EstadoSunat estado, String codigo,
            String descripcion, boolean conCdr) {
        String nombre = orden.documento().nombreDeArchivo(orden.emisor().ruc());
        return new ResultadoDeEmision(orden.id(), orden.empresaId(), OrdenDeEmision.Operacion.EMITIR,
                estado, codigo, descripcion, List.of("4252 - El dato ingresado como observación"),
                ClavesDelBus.xml(RUC_DEMO, nombre), conCdr ? ClavesDelBus.cdr(RUC_DEMO, nombre) : null,
                "hash=", null, null, Instant.now());
    }

    @Test
    @DisplayName("Una factura publica su orden al confirmar, con todo lo que el XML necesita")
    void laFacturaPublicaSuOrden() throws Exception {
        JsonNode documento = emitir("FACTURA", SERIE_FACTURA, CLIENTE_RUC);
        String documentoId = documento.path("id").asString();

        JsonNode estado = sunat(documentoId);
        assertThat(estado.path("estado").asString()).isEqualTo("EN_COLA");
        assertThat(estado.path("intentos").asInt()).isEqualTo(1);
        assertThat(estado.path("xmlDisponible").asBoolean()).isFalse();

        OrdenDeEmision orden = ordenDe(UUID.fromString(estado.path("comprobanteId").asString()));
        assertThat(orden.operacion()).isEqualTo(OrdenDeEmision.Operacion.EMITIR);
        assertThat(orden.emisor().ruc()).isEqualTo(RUC_DEMO);
        assertThat(orden.emisor().razonSocial()).isEqualTo("COMERCIAL DEMO S.A.C.");
        assertThat(orden.emisor().codigoEstablecimiento()).isEqualTo("0000");
        assertThat(orden.emisor().usuarioSol()).isEqualTo("MODDATOS");
        assertThat(orden.documento().tipo()).isEqualTo("01");
        assertThat(orden.documento().serie()).isEqualTo("F001");
        assertThat(orden.documento().numero()).isEqualTo(documento.path("numero").asLong());
        assertThat(orden.documento().adquirente().tipoDocumento()).isEqualTo("6");
        assertThat(orden.documento().adquirente().numeroDocumento()).isEqualTo("20131312955");
        assertThat(orden.documento().lineas()).hasSize(1);
        assertThat(orden.documento().lineas().get(0).unidad()).isEqualTo("ZZ");
        assertThat(orden.documento().lineas().get(0).afectacion()).isEqualTo("10");
        assertThat(orden.documento().total()).isEqualByComparingTo("80.00");
        assertThat(orden.documento().totalIgv()).isEqualByComparingTo("12.20");
        assertThat(orden.documento().moneda()).isEqualTo("PEN");
        assertThat(orden.documento().nombreDeArchivo(RUC_DEMO))
                .isEqualTo(RUC_DEMO + "-01-F001-" + String.format("%08d", orden.documento().numero()));

        // El listado de comprobantes trae el estado ante SUNAT; el de notas de venta, no.
        mockMvc.perform(comoAdministrador(get("/api/v1/ventas/comprobantes")).param("tipo", "FACTURA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoSunat").value("EN_COLA"));
    }

    @Test
    @DisplayName("Aceptado por SUNAT: el comprobante guarda el CDR y el documento pasa a EMITIDO")
    void aceptado() throws Exception {
        String documentoId = emitir("BOLETA", SERIE_BOLETA, CLIENTE_DNI).path("id").asString();
        OrdenDeEmision orden = ordenDe(UUID.fromString(sunat(documentoId).path("comprobanteId").asString()));
        assertThat(orden.documento().adquirente().tipoDocumento()).isEqualTo("1");

        bus.depositarResultado(resultado(orden, EstadoSunat.ACEPTADO, "0",
                "La Boleta numero B001-1, ha sido aceptada", true));

        // La consulta encuentra el resultado y lo aplica.
        JsonNode estado = sunat(documentoId);
        assertThat(estado.path("estado").asString()).isEqualTo("ACEPTADO");
        assertThat(estado.path("codigo").asString()).isEqualTo("0");
        assertThat(estado.path("cdrDisponible").asBoolean()).isTrue();
        assertThat(estado.path("xmlDisponible").asBoolean()).isTrue();
        assertThat(estado.path("resumenFirma").asString()).isEqualTo("hash=");
        assertThat(estado.path("observaciones").get(0).asString()).startsWith("4252");
        assertThat(estado.path("admiteReintento").asBoolean()).isFalse();

        mockMvc.perform(comoAdministrador(get("/api/v1/ventas/comprobantes/" + documentoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EMITIDO"));

        // Las descargas dan una URL del bus.
        mockMvc.perform(comoAdministrador(get("/api/v1/ventas/comprobantes/" + documentoId + "/sunat/xml")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("documentos/" + RUC_DEMO + "/")));
        mockMvc.perform(comoAdministrador(get("/api/v1/ventas/comprobantes/" + documentoId + "/sunat/cdr")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("/R-")));

        // Un aceptado no se vuelve a enviar.
        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/comprobantes/" + documentoId + "/sunat/reintento")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("reintento_no_admitido"));
    }

    @Test
    @DisplayName("Rechazado: el código y la descripción de SUNAT se ven, y el reintento publica otra orden")
    void rechazadoYReintento() throws Exception {
        String documentoId = emitir("BOLETA", SERIE_BOLETA, CLIENTE_DNI).path("id").asString();
        UUID comprobanteId = UUID.fromString(sunat(documentoId).path("comprobanteId").asString());

        // En cola hace un instante: todavía no se puede reintentar.
        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/comprobantes/" + documentoId + "/sunat/reintento")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("emision_en_curso"));

        bus.depositarResultado(resultado(ordenDe(comprobanteId), EstadoSunat.RECHAZADO, "2335",
                "El documento electrónico ingresado ha sido alterado", false));

        JsonNode estado = sunat(documentoId);
        assertThat(estado.path("estado").asString()).isEqualTo("RECHAZADO");
        assertThat(estado.path("codigo").asString()).isEqualTo("2335");
        assertThat(estado.path("descripcion").asString()).contains("alterado");
        assertThat(estado.path("xmlDisponible").asBoolean()).as("el XML se conserva").isTrue();
        assertThat(estado.path("cdrDisponible").asBoolean()).isFalse();
        assertThat(estado.path("admiteReintento").asBoolean()).isTrue();
        // El documento sigue pendiente para el negocio.
        mockMvc.perform(comoAdministrador(get("/api/v1/ventas/comprobantes/" + documentoId)))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        int ordenesAntes = bus.ordenes().size();
        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/comprobantes/" + documentoId + "/sunat/reintento")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_COLA"))
                .andExpect(jsonPath("$.intentos").value(2));
        assertThat(bus.ordenes()).hasSize(ordenesAntes + 1);
        assertThat(bus.ordenes().get(ordenesAntes).id()).isEqualTo(comprobanteId);
        // Mismo número: para SUNAT el rechazado no existe.
        assertThat(bus.ordenes().get(ordenesAntes).documento().numero())
                .isEqualTo(ordenDe(comprobanteId).documento().numero());
    }

    @Test
    @DisplayName("Sin certificado ni clave SOL no se emiten boletas ni facturas, y no se gasta correlativo")
    void sinCredencialesNoSeEmite() throws Exception {
        String siguienteAntes = siguienteNumero("BOLETA");
        jdbc.sql("""
                update empresa set usuario_sol = null, certificado_cargado_en = null,
                       certificado_verificado_en = null, certificado_sujeto = null,
                       certificado_vence_en = null, certificado_error = null
                 where id = ?::uuid""").param(EMPRESA_ADMINISTRADA).update();

        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/comprobantes"))
                        .content(cuerpo("BOLETA", cajaAbierta(), SERIE_BOLETA, CLIENTE_DNI)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("emision_no_configurada"));
        assertThat(siguienteNumero("BOLETA")).isEqualTo(siguienteAntes);

        // La nota de venta sigue saliendo: no se declara.
        mockMvc.perform(comoAdministrador(post("/api/v1/ventas/notas-de-venta")).content("""
                        {"cajaId": "%s", "serieId": "00000000-0000-4000-8000-0000000000a0",
                         "lineas": [{"productoId": "%s", "cantidad": 1}],
                         "pagos": [{"forma": "EFECTIVO", "monto": 80.00}]}
                        """.formatted(cajaAbierta(), INSTALACION)))
                .andExpect(status().isCreated());
    }

    private String siguienteNumero(String tipo) throws Exception {
        JsonNode series = leer(mockMvc.perform(comoAdministrador(get("/api/v1/ventas/series"))
                        .param("tipo", tipo).param("sucursalId", MATRIZ))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode s : series) {
            if (SERIE_BOLETA.equals(s.path("id").asString())) {
                return s.path("siguienteNumero").asString();
            }
        }
        throw new AssertionError("no está la serie de boletas");
    }

    @Test
    @DisplayName("Cargar el certificado: URL de subida, confirmación, verificación del Emisor y paso a producción")
    void cargaDelCertificado() throws Exception {
        JsonNode autorizacion = leer(mockMvc.perform(comoAdministrador(post("/api/v1/configuracion/empresa/emision/carga")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(autorizacion.path("claveCertificado").asString()).isEqualTo("certificados/" + RUC_DEMO + ".pfx");
        assertThat(autorizacion.path("claveCredenciales").asString()).isEqualTo("credenciales/" + RUC_DEMO + ".json");
        assertThat(autorizacion.path("urlCertificado").asString()).contains("certificados/" + RUC_DEMO + ".pfx");
        assertThat(autorizacion.path("validezSegundos").asLong()).isEqualTo(300);

        // Sin haber subido nada, la confirmación se rechaza y dice qué falta.
        // (Se limpia lo que otra prueba pudo fingir, para que la regla se ejercite.)
        bus.olvidarObjeto(ClavesDelBus.certificado(RUC_DEMO));
        bus.olvidarObjeto(ClavesDelBus.credenciales(RUC_DEMO));
        mockMvc.perform(comoAdministrador(put("/api/v1/configuracion/empresa/emision/carga"))
                        .content("{\"usuarioSol\": \"MODDATOS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(org.hamcrest.Matchers.oneOf(
                        "certificado_no_subido", "credenciales_no_subidas")));

        bus.fingirSubida(ClavesDelBus.certificado(RUC_DEMO));
        bus.fingirSubida(ClavesDelBus.credenciales(RUC_DEMO));
        int ordenesAntes = bus.ordenes().size();
        JsonNode cargada = leer(mockMvc.perform(comoAdministrador(put("/api/v1/configuracion/empresa/emision/carga"))
                        .content("{\"usuarioSol\": \"MODDATOS\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(cargada.path("puedeEmitir").asBoolean()).isTrue();
        assertThat(cargada.path("certificadoCargadoEn").isNull()).isFalse();
        assertThat(cargada.path("certificadoVerificadoEn").isNull()).as("todavía sin verificar").isTrue();

        // Se encoló la verificación, sin documento y con el emisor.
        assertThat(bus.ordenes()).hasSize(ordenesAntes + 1);
        OrdenDeEmision verificacion = bus.ordenes().get(ordenesAntes);
        assertThat(verificacion.operacion()).isEqualTo(OrdenDeEmision.Operacion.VERIFICAR_CREDENCIALES);
        assertThat(verificacion.documento()).isNull();
        assertThat(verificacion.emisor().ruc()).isEqualTo(RUC_DEMO);

        // Sin verificar, producción se niega.
        mockMvc.perform(comoAdministrador(put("/api/v1/configuracion/empresa/emision/modo"))
                        .content("{\"modo\": \"PRODUCCION\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("sin_certificado_vigente"));

        // El Emisor abrió el certificado: la próxima consulta lo aplica.
        bus.depositarResultado(new ResultadoDeEmision(verificacion.id(), verificacion.empresaId(),
                OrdenDeEmision.Operacion.VERIFICAR_CREDENCIALES, EstadoSunat.ACEPTADO, "0",
                "El certificado abre", List.of(), null, null, null,
                "CN=COMERCIAL DEMO S.A.C., O=DEMO", LocalDate.now().plusYears(1), Instant.now()));
        JsonNode verificada = leer(mockMvc.perform(comoAdministrador(get("/api/v1/configuracion/empresa/emision")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(verificada.path("certificadoSujeto").asString()).startsWith("CN=COMERCIAL DEMO");
        assertThat(verificada.path("certificadoVerificadoEn").isNull()).isFalse();
        assertThat(verificada.path("certificadoError").isNull()).isTrue();

        mockMvc.perform(comoAdministrador(put("/api/v1/configuracion/empresa/emision/modo"))
                        .content("{\"modo\": \"PRODUCCION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoSunat").value("PRODUCCION"));
        mockMvc.perform(comoAdministrador(put("/api/v1/configuracion/empresa/emision/modo"))
                        .content("{\"modo\": \"BETA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoSunat").value("BETA"));
    }

    @Test
    @DisplayName("Un certificado que no abre se ve con su motivo y no deja pasar a producción")
    void certificadoQueNoAbre() throws Exception {
        bus.fingirSubida(ClavesDelBus.certificado(RUC_DEMO));
        bus.fingirSubida(ClavesDelBus.credenciales(RUC_DEMO));
        mockMvc.perform(comoAdministrador(put("/api/v1/configuracion/empresa/emision/carga"))
                        .content("{\"usuarioSol\": \"MODDATOS\"}"))
                .andExpect(status().isOk());
        OrdenDeEmision verificacion = bus.ordenes().get(bus.ordenes().size() - 1);

        bus.depositarResultado(new ResultadoDeEmision(verificacion.id(), verificacion.empresaId(),
                OrdenDeEmision.Operacion.VERIFICAR_CREDENCIALES, EstadoSunat.ERROR_ENVIO,
                "CERTIFICADO_NO_ABRE", "La contraseña no corresponde al certificado", List.of(),
                null, null, null, null, null, Instant.now()));

        mockMvc.perform(comoAdministrador(get("/api/v1/configuracion/empresa/emision")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificadoError").value(org.hamcrest.Matchers.containsString("contraseña")))
                .andExpect(jsonPath("$.puedeEmitir").value(true));
        mockMvc.perform(comoAdministrador(put("/api/v1/configuracion/empresa/emision/modo"))
                        .content("{\"modo\": \"PRODUCCION\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("sin_certificado_vigente"));
    }
}
