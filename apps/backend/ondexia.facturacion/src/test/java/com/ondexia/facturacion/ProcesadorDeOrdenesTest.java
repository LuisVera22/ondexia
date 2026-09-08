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

        @Override
        public Optional<byte[]> leer(String clave) {
            return Optional.ofNullable(objetos.get(clave));
        }

        @Override
        public void escribir(String clave, byte[] contenido, String tipoContenido) {
            objetos.put(clave, contenido);
        }

        @Override
        public void borrar(String clave) {
            objetos.remove(clave);
        }
    }

    /** SUNAT fingida: devuelve lo que se le programe y recuerda lo que recibió. */
    static class SunatFingida extends ClienteSunat {
        RespuestaSunat respuesta;
        String urlUsada;
        String usuarioUsado;
        String claveUsada;
        String zipUsado;

        SunatFingida() {
            super(Duration.ofSeconds(1));
        }

        @Override
        public RespuestaSunat enviar(String urlServicio, String ruc, String usuarioSol, String claveSol,
                String nombreZip, byte[] zip) {
            this.urlUsada = urlServicio;
            this.usuarioUsado = ruc + usuarioSol;
            this.claveUsada = claveSol;
            this.zipUsado = nombreZip;
            assertThat(new String(Empaquetador.primerXml(zip), StandardCharsets.ISO_8859_1))
                    .contains("<ds:Signature");
            return respuesta;
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
        try (var pfx = getClass().getResourceAsStream("/certificado-prueba.pfx")) {
            bus.escribir(ClavesDelBus.certificado(Ordenes.RUC), pfx.readAllBytes(), "application/x-pkcs12");
        }
        bus.escribir(ClavesDelBus.credenciales(Ordenes.RUC),
                "{\"claveCertificado\": \"prueba\", \"claveSol\": \"MODDATOS\"}".getBytes(), "application/json");
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
        assertThat(resultado.claveXml()).isEqualTo("documentos/20100000009/20100000009-03-B001-00000012.xml");
        assertThat(resultado.claveCdr()).isEqualTo("documentos/20100000009/R-20100000009-03-B001-00000012.zip");
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
    @DisplayName("En producción se envía a la URL de producción")
    void produccion() {
        sunat.respuesta = RespuestaSunat.sinRespuesta("SIN_CONEXION", "x");
        var beta = Ordenes.boleta(UUID.randomUUID());
        var prod = new OrdenDeEmision(beta.id(), beta.operacion(), beta.empresaId(),
                com.ondexia.domain.identidad.ModoSunat.PRODUCCION, beta.emisor(), beta.documento(),
                beta.creadaEn());
        procesador.procesar(prod);
        assertThat(sunat.urlUsada).isEqualTo("https://prod.local/billService");
    }
}
