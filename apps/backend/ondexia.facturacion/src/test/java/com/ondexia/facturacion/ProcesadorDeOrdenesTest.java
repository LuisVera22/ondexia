package com.ondexia.facturacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondexia.domain.comprobante.ClavesDelBus;
import com.ondexia.domain.comprobante.EstadoSunat;
import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.comprobante.ResultadoDeEmision;
import com.ondexia.facturacion.bus.AlmacenDelBus;
import com.ondexia.facturacion.sunat.ClienteSunat;
import com.ondexia.facturacion.sunat.ConstructorDeComprobante;
import com.ondexia.facturacion.sunat.Empaquetador;
import com.ondexia.facturacion.sunat.FirmadorDeComprobante;
import com.ondexia.facturacion.sunat.LectorDeRespuestaSunat;
import com.ondexia.facturacion.sunat.LectorDeRespuestaSunatTest;
import com.ondexia.facturacion.sunat.RespuestaSunat;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Una orden de principio a fin con el bus en memoria y una SUNAT fingida: lo
 * que queda en el bus y lo que dice el resultado en cada desenlace.
 */
class ProcesadorDeOrdenesTest {

    /** El bus como mapa. */
    static class BusEnMemoria implements AlmacenDelBus {
        final Map<String, byte[]> objetos = new HashMap<>();
        boolean rechazarZip;

        @Override
        public Optional<byte[]> leer(String clave) {
            return Optional.ofNullable(objetos.get(clave));
        }

        @Override
        public void escribir(String clave, byte[] contenido, String tipoContenido) {
            if (rechazarZip && clave.startsWith(ClavesDelBus.DOCUMENTOS) && clave.endsWith(".zip")) {
                throw new IllegalStateException("No se pudo conservar el ZIP original.");
            }
            if (clave.startsWith(ClavesDelBus.DOCUMENTOS) && objetos.containsKey(clave)) {
                throw new IllegalStateException("No se sobrescribe un documento original.");
            }
            objetos.put(clave, contenido.clone());
        }

        @Override
        public void borrar(String clave) {
            objetos.remove(clave);
        }
    }

    /** SUNAT fingida: devuelve lo que se le programe y recuerda lo que recibió. */
    static class SunatFingida extends ClienteSunat {
        RespuestaSunat respuesta;
        RespuestaSunat respuestaDeResumen;
        String urlUsada;
        String usuarioUsado;
        String claveUsada;
        String zipUsado;
        byte[] zipEnviado;
        String ticketConsultado;

        SunatFingida() {
            super(Duration.ofSeconds(1));
        }

        @Override
        public RespuestaSunat enviar(String urlServicio, String ruc, String usuarioSol, String claveSol,
                String nombreZip, byte[] zip) {
            anotar(urlServicio, ruc, usuarioSol, claveSol, nombreZip, zip);
            assertThat(new String(Empaquetador.primerXml(zip), StandardCharsets.ISO_8859_1))
                    .contains("<ds:Signature");
            return respuesta;
        }

        @Override
        public RespuestaSunat enviarResumen(String urlServicio, String ruc, String usuarioSol,
                String claveSol, String nombreZip, byte[] zip) {
            anotar(urlServicio, ruc, usuarioSol, claveSol, nombreZip, zip);
            return respuestaDeResumen;
        }

        @Override
        public RespuestaSunat consultarTicket(String urlServicio, String ruc, String usuarioSol,
                String claveSol, String ticket) {
            this.urlUsada = urlServicio;
            this.ticketConsultado = ticket;
            return respuesta;
        }

        private void anotar(String urlServicio, String ruc, String usuarioSol, String claveSol,
                String nombreZip, byte[] zip) {
            this.urlUsada = urlServicio;
            this.usuarioUsado = ruc + usuarioSol;
            this.claveUsada = claveSol;
            this.zipUsado = nombreZip;
            this.zipEnviado = zip;
        }
    }

    private final BusEnMemoria bus = new BusEnMemoria();
    private final SunatFingida sunat = new SunatFingida();
    private final ObjectMapper json = new ObjectMapper();
    private ProcesadorDeOrdenes procesador;

    @BeforeEach
    void preparar() throws Exception {
        procesador = new ProcesadorDeOrdenes(bus, sunat, new ConstructorDeComprobante(),
                new FirmadorDeComprobante(),
                new PropiedadesEmision("", null, "https://beta.local/billService",
                        "https://prod.local/billService", Duration.ofSeconds(5)),
                json, Clock.fixed(Instant.parse("2026-09-08T15:20:00Z"), ZoneOffset.UTC));
        bus.escribir(ClavesDelBus.certificado(Ordenes.RUC), CertificadoDePrueba.bytes(),
                "application/x-pkcs12");
        bus.escribir(ClavesDelBus.credenciales(Ordenes.RUC),
                ("{\"claveCertificado\": \"" + CertificadoDePrueba.CLAVE + "\", \"claveSol\": \"MODDATOS\"}")
                        .getBytes(), "application/json");
    }

    private ResultadoDeEmision resultadoGuardado(OrdenDeEmision orden) {
        return json.readValue(bus.objetos.get(ClavesDelBus.resultado(orden.empresaId(), orden.id())),
                ResultadoDeEmision.class);
    }

    @Test
    @DisplayName("Aceptada: XML firmado y CDR en el bus, resultado con el resumen de la firma")
    void aceptada() {
        sunat.respuesta = new LectorDeRespuestaSunat().leerCdr(
                LectorDeRespuestaSunatTest.cdr("0", "La Boleta numero B001-12, ha sido aceptada",
                        "4252 - observación"));
        OrdenDeEmision orden = Ordenes.boleta(UUID.randomUUID());
        bus.escribir(ClavesDelBus.pendiente(orden.empresaId(), orden.id()), json.writeValueAsBytes(orden),
                "application/json");

        ResultadoDeEmision resultado = procesador.procesarPendiente(
                ClavesDelBus.pendiente(orden.empresaId(), orden.id()));

        assertThat(resultado.estado()).isEqualTo(EstadoSunat.ACEPTADO);
        assertThat(resultado.codigo()).isEqualTo("0");
        assertThat(resultado.observaciones()).containsExactly("4252 - observación");
        assertThat(resultado.resumenFirma()).isNotBlank();
        assertThat(resultado.claveXml()).startsWith("documentos/20100000009/originales/" + orden.id() + "/").endsWith("/20100000009-03-B001-00000012.xml");
        assertThat(resultado.claveCdr()).startsWith("documentos/20100000009/originales/" + orden.id() + "/").endsWith("/R-20100000009-03-B001-00000012.zip");
        assertThat(resultado.procesadoEn()).isEqualTo(Instant.parse("2026-09-08T15:20:00Z"));
        assertThat(bus.objetos).containsKey(resultado.claveXml()).containsKey(resultado.claveCdr());
        assertThat(bus.objetos).as("la orden se borra al terminar")
                .doesNotContainKey(ClavesDelBus.pendiente(orden.empresaId(), orden.id()));
        assertThat(resultadoGuardado(orden).estado()).isEqualTo(EstadoSunat.ACEPTADO);

        // A la beta, con RUC+usuario SOL y la clave SOL del bus.
        assertThat(sunat.urlUsada).isEqualTo("https://beta.local/billService");
        assertThat(sunat.usuarioUsado).isEqualTo("20100000009MODDATOS");
        assertThat(sunat.claveUsada).isEqualTo("MODDATOS");
        assertThat(sunat.zipUsado).isEqualTo("20100000009-03-B001-00000012.zip");
    }

    @Test
    @DisplayName("Cada intento conserva XML, ZIP enviado y CDR originales sin sustituir el anterior")
    void reintentoConservaOriginales() {
        byte[] primerCdr = LectorDeRespuestaSunatTest.cdr("0", "Primera constancia");
        sunat.respuesta = new LectorDeRespuestaSunat().leerCdr(primerCdr);
        var orden = Ordenes.boleta(UUID.randomUUID());
        var primero = procesador.procesar(orden);
        byte[] primerZipEnviado = sunat.zipEnviado.clone();
        byte[] primerXmlEnviado = Empaquetador.primerXml(primerZipEnviado);
        assertThat(primero.claveXml()).contains("/originales/" + orden.id() + "/");
        assertThat(bus.objetos.get(primero.claveXml())).isEqualTo(primerXmlEnviado);
        assertThat(bus.objetos.get(primero.claveXml().replaceFirst("\\.xml$", ".zip")))
                .isEqualTo(primerZipEnviado);
        assertThat(bus.objetos.get(primero.claveCdr())).isEqualTo(primerCdr);
        String prefijo = primero.claveXml().substring(0, primero.claveXml().lastIndexOf('/') + 1);
        var envio = json.readTree(bus.objetos.get(prefijo + "envio.json"));
        var recepcion = json.readTree(bus.objetos.get(prefijo + "recepcion.json"));
        assertThat(envio.path("ordenId").asString()).isEqualTo(orden.id().toString());
        assertThat(envio.path("empresaId").asString()).isEqualTo(orden.empresaId().toString());
        assertThat(envio.path("registradoEn").asString()).isEqualTo("2026-09-08T15:20:00Z");
        assertThat(recepcion.path("codigo").asString()).isEqualTo("0");
        assertThat(new String(bus.objetos.get(prefijo + "envio.json"), StandardCharsets.UTF_8))
                .doesNotContain("claveSol", "claveCertificado", "MODDATOS");
        byte[] segundoCdr = LectorDeRespuestaSunatTest.cdr("0", "Segunda constancia");
        sunat.respuesta = new LectorDeRespuestaSunat().leerCdr(segundoCdr);
        var segundo = procesador.procesar(orden);
        assertThat(segundo.claveXml()).isNotEqualTo(primero.claveXml());
        assertThat(segundo.claveCdr()).isNotEqualTo(primero.claveCdr());
        assertThat(bus.objetos.get(primero.claveXml())).isEqualTo(primerXmlEnviado);
        assertThat(bus.objetos.get(primero.claveCdr())).isEqualTo(primerCdr);
        assertThat(bus.objetos.get(segundo.claveCdr())).isEqualTo(segundoCdr);
    }

    @Test
    @DisplayName("Si no se conserva el ZIP original, no se llama a SUNAT")
    void sinArchivoOriginalNoEnvia() {
        bus.rechazarZip = true;
        sunat.respuesta = new LectorDeRespuestaSunat().leerCdr(
                LectorDeRespuestaSunatTest.cdr("0", "Aceptada"));
        var resultado = procesador.procesar(Ordenes.boleta(UUID.randomUUID()));
        assertThat(resultado.estado()).isEqualTo(EstadoSunat.ERROR_ENVIO);
        assertThat(resultado.codigo()).isEqualTo("EMISOR_FALLO");
        assertThat(sunat.urlUsada).isNull();
        assertThat(sunat.zipEnviado).isNull();
    }

    @Test
    @DisplayName("Rechazada por SUNAT: el XML queda, el CDR no, y el código se conserva")
    void rechazada() {
        sunat.respuesta = new RespuestaSunat(RespuestaSunat.Tipo.FALLO, "2335",
                "El documento electrónico ingresado ha sido alterado", List.of(), null);
        OrdenDeEmision orden = Ordenes.boleta(UUID.randomUUID());

        ResultadoDeEmision resultado = procesador.procesar(orden);

        assertThat(resultado.estado()).isEqualTo(EstadoSunat.RECHAZADO);
        assertThat(resultado.codigo()).isEqualTo("2335");
        assertThat(resultado.claveXml()).isNotNull();
        assertThat(resultado.claveCdr()).isNull();
        assertThat(bus.objetos).containsKey(resultado.claveXml());
    }

    @Test
    @DisplayName("SUNAT no responde: error de envío, reintentable tal cual")
    void sinRespuesta() {
        sunat.respuesta = RespuestaSunat.sinRespuesta("SIN_CONEXION", "No se pudo conectar con SUNAT");
        ResultadoDeEmision resultado = procesador.procesar(Ordenes.boleta(UUID.randomUUID()));
        assertThat(resultado.estado()).isEqualTo(EstadoSunat.ERROR_ENVIO);
        assertThat(resultado.codigo()).isEqualTo("SIN_CONEXION");
    }

    @Test
    @DisplayName("Sin credenciales o con la contraseña mal, el resultado lo dice sin llamar a SUNAT")
    void sinCredenciales() {
        bus.borrar(ClavesDelBus.credenciales(Ordenes.RUC));
        ResultadoDeEmision resultado = procesador.procesar(Ordenes.boleta(UUID.randomUUID()));
        assertThat(resultado.estado()).isEqualTo(EstadoSunat.ERROR_ENVIO);
        assertThat(resultado.codigo()).isEqualTo("SIN_CREDENCIALES");
        assertThat(sunat.urlUsada).isNull();

        bus.escribir(ClavesDelBus.credenciales(Ordenes.RUC),
                "{\"claveCertificado\": \"otra\", \"claveSol\": \"MODDATOS\"}".getBytes(), "application/json");
        resultado = procesador.procesar(Ordenes.boleta(UUID.randomUUID()));
        assertThat(resultado.codigo()).isEqualTo("CERTIFICADO_NO_ABRE");
        assertThat(resultado.descripcion()).contains("contraseña").doesNotContain("otra");
    }

    @Test
    @DisplayName("Verificar credenciales abre el certificado y devuelve sujeto y vencimiento")
    void verificar() {
        ResultadoDeEmision resultado = procesador.procesar(Ordenes.verificacion(UUID.randomUUID()));
        assertThat(resultado.operacion()).isEqualTo(OrdenDeEmision.Operacion.VERIFICAR_CREDENCIALES);
        assertThat(resultado.estado()).isEqualTo(EstadoSunat.ACEPTADO);
        assertThat(resultado.certificadoSujeto()).contains("CN=CERTIFICADO DE PRUEBA ONDEXIA");
        assertThat(resultado.certificadoVenceEn()).isNotNull();
        assertThat(sunat.urlUsada).as("no se envía nada a SUNAT").isNull();
    }

    @Test
    @DisplayName("La comunicación de baja: se firma, se envía y vuelve con ticket, no con constancia")
    void comunicacionDeBaja() {
        byte[] respuestaOriginal = """
                <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap-env:Body><br:sendSummaryResponse xmlns:br="http://service.sunat.gob.pe">
                    <ticket>1554895</ticket>
                  </br:sendSummaryResponse></soap-env:Body>
                </soap-env:Envelope>
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        sunat.respuestaDeResumen = new LectorDeRespuestaSunat().leerTicket(200, respuestaOriginal);
        OrdenDeEmision orden = Ordenes.baja(UUID.randomUUID());

        ResultadoDeEmision resultado = procesador.procesar(orden);

        assertThat(resultado.estado()).isEqualTo(EstadoSunat.EN_PROCESO);
        assertThat(resultado.codigo()).as("el ticket va en el código").isEqualTo("1554895");
        assertThat(resultado.claveXml())
                .startsWith("documentos/20100000009/originales/" + orden.id() + "/").endsWith("/20100000009-RA-20260909-1.xml");
        assertThat(bus.objetos).containsKey(resultado.claveXml());
        assertThat(sunat.zipUsado).isEqualTo("20100000009-RA-20260909-1.zip");
        String prefijo = resultado.claveXml().substring(0, resultado.claveXml().lastIndexOf('/') + 1);
        assertThat(bus.objetos.get(prefijo + "respuesta.xml")).isEqualTo(respuestaOriginal);
        // Firmada, como cualquier cosa que sale hacia SUNAT.
        assertThat(new String(Empaquetador.primerXml(sunat.zipEnviado),
                java.nio.charset.StandardCharsets.ISO_8859_1)).contains("<ds:Signature");
    }

    @Test
    @DisplayName("Consultar el ticket: si sigue en proceso lo dice, y si resuelve guarda el CDR")
    void consultaDelTicket() {
        var lector = new LectorDeRespuestaSunat();
        sunat.respuesta = lector.leer(200, """
                <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap-env:Body><br:getStatusResponse xmlns:br="http://service.sunat.gob.pe">
                    <status><statusCode>98</statusCode><statusMessage>En proceso</statusMessage></status>
                  </br:getStatusResponse></soap-env:Body>
                </soap-env:Envelope>
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        byte[] respuestaOriginal = sunat.respuesta.respuestaOriginal();
        var ordenConsulta = Ordenes.consultaDeTicket(UUID.randomUUID(), "1554895");
        var enProceso = procesador.procesar(ordenConsulta);
        var originales = bus.objetos.entrySet().stream()
                .filter(entrada -> entrada.getKey().startsWith("documentos/20100000009/originales/" + ordenConsulta.id() + "/"))
                .filter(entrada -> entrada.getKey().endsWith("/respuesta.xml")).toList();
        assertThat(originales).hasSize(1);
        assertThat(originales.getFirst().getValue()).isEqualTo(respuestaOriginal);
        assertThat(enProceso.estado()).isEqualTo(EstadoSunat.EN_PROCESO);
        assertThat(enProceso.claveCdr()).isNull();

        sunat.respuesta = lector.leerCdr(LectorDeRespuestaSunatTest.cdr("0", "Aceptada"));
        var aceptada = procesador.procesar(Ordenes.consultaDeTicket(UUID.randomUUID(), "1554895"));

        assertThat(aceptada.estado()).isEqualTo(EstadoSunat.ACEPTADO);
        assertThat(aceptada.claveCdr()).startsWith("documentos/20100000009/originales/").endsWith("/R-ticket.zip");
        assertThat(bus.objetos).containsKey(aceptada.claveCdr());
    }

    @Test
    @DisplayName("En producción se envía a la URL de producción")
    void produccion() {
        sunat.respuesta = RespuestaSunat.sinRespuesta("SIN_CONEXION", "x");
        var beta = Ordenes.boleta(UUID.randomUUID());
        var prod = OrdenDeEmision.paraEmitir(beta.id(), beta.empresaId(),
                com.ondexia.domain.identidad.ModoSunat.PRODUCCION, beta.emisor(), beta.documento(),
                beta.creadaEn());
        procesador.procesar(prod);
        assertThat(sunat.urlUsada).isEqualTo("https://prod.local/billService");
    }
}
