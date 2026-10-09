package com.ondexia.facturacion;

import com.ondexia.domain.comprobante.ClavesDelBus;
import com.ondexia.domain.comprobante.EstadoSunat;
import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.comprobante.ResultadoDeEmision;
import com.ondexia.domain.identidad.ModoSunat;
import com.ondexia.facturacion.bus.AlmacenDelBus;
import com.ondexia.facturacion.sunat.ClienteSunat;
import com.ondexia.facturacion.sunat.ConstructorDeBaja;
import com.ondexia.facturacion.sunat.ConstructorDeComprobante;
import com.ondexia.facturacion.sunat.Empaquetador;
import com.ondexia.facturacion.sunat.FirmadorDeComprobante;
import com.ondexia.facturacion.sunat.RespuestaSunat;
import io.github.project.openubl.xbuilder.signature.CertificateDetails;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Una orden, de principio a fin (doc 14 §5): credenciales, XML, firma, envío,
 * CDR y resultado. Siempre deja un resultado en el bus, también cuando algo
 * falla antes de llegar a SUNAT: un fallo silencioso dejaría el comprobante en
 * cola para siempre.
 *
 * <p>Lo que puede salir mal se clasifica en dos: lo que SUNAT rechazó
 * ({@code RECHAZADO}, se corrige y se reenvía con el mismo número) y lo que no
 * llegó o no se pudo hacer ({@code ERROR_ENVIO}, se reintenta tal cual). El
 * código y la descripción van siempre, para que la persona vea en pantalla lo
 * mismo que diría SUNAT.
 */
public class ProcesadorDeOrdenes {

    private static final Logger LOG = LoggerFactory.getLogger(ProcesadorDeOrdenes.class);

    private final AlmacenDelBus bus;
    private final ClienteSunat sunat;
    private final ConstructorDeComprobante constructor;
    private final ConstructorDeBaja constructorDeBaja = new ConstructorDeBaja();
    private final FirmadorDeComprobante firmador;
    private final PropiedadesEmision propiedades;
    private final ObjectMapper json;
    private final Clock reloj;

    public ProcesadorDeOrdenes(AlmacenDelBus bus, ClienteSunat sunat,
            ConstructorDeComprobante constructor, FirmadorDeComprobante firmador,
            PropiedadesEmision propiedades, ObjectMapper json, Clock reloj) {
        this.bus = bus;
        this.sunat = sunat;
        this.constructor = constructor;
        this.firmador = firmador;
        this.propiedades = propiedades;
        this.json = json;
        this.reloj = reloj;
    }

    /** Lee la orden de {@code pendientes/}, la procesa, deja el resultado y la borra. */
    public ResultadoDeEmision procesarPendiente(String clave) {
        byte[] contenido = bus.leer(clave).orElseThrow(() -> new IllegalStateException(
                "La orden " + clave + " ya no está en el bus."));
        OrdenDeEmision orden = json.readValue(contenido, OrdenDeEmision.class);
        ResultadoDeEmision resultado = procesar(orden);
        bus.borrar(clave);
        return resultado;
    }

    /** Procesa y deja el resultado en {@code resultados/}. La orden puede venir de cualquier sitio. */
    public ResultadoDeEmision procesar(OrdenDeEmision orden) {
        ResultadoDeEmision resultado;
        try {
            resultado = switch (orden.operacion()) {
                case EMITIR -> emitir(orden);
                case ENVIAR_BAJA -> enviarBaja(orden);
                case CONSULTAR_TICKET -> consultarTicket(orden);
                case VERIFICAR_CREDENCIALES -> verificar(orden);
            };
        } catch (RuntimeException inesperado) {
            LOG.error("La orden {} falló antes de producir resultado: {}", orden.id(),
                    inesperado.toString());
            resultado = fallo(orden, "EMISOR_FALLO",
                    "El Emisor no pudo procesar la orden: " + inesperado.getMessage(), null, null);
        }
        bus.escribir(ClavesDelBus.resultado(orden.empresaId(), orden.id()),
                json.writeValueAsBytes(resultado), "application/json");
        LOG.info("Orden {} ({}) → {} {}", orden.id(), orden.operacion(), resultado.estado(),
                resultado.codigo());
        return resultado;
    }

    private ResultadoDeEmision emitir(OrdenDeEmision orden) {
        String ruc = orden.emisor().ruc();
        Optional<Credenciales> credenciales = credencialesDe(ruc);
        if (credenciales.isEmpty()) {
            return fallo(orden, "SIN_CREDENCIALES",
                    "No hay certificado o credenciales cargados para el RUC " + ruc + ".", null, null);
        }
        CertificateDetails certificado;
        try {
            certificado = abrirCertificado(ruc, credenciales.get());
        } catch (FirmadorDeComprobante.CertificadoNoAbre e) {
            return fallo(orden, "CERTIFICADO_NO_ABRE", e.getMessage(), null, null);
        }

        String nombre = orden.documento().nombreDeArchivo(ruc);
        String xml = constructor.construir(orden);
        FirmadorDeComprobante.Firmado firmado = firmador.firmar(xml, certificado);
        String claveXml = ClavesDelBus.xml(ruc, nombre);
        bus.escribir(claveXml, firmado.xml(), "application/xml");

        byte[] zip = Empaquetador.comprimir(nombre + ".xml", firmado.xml());
        RespuestaSunat respuesta = sunat.enviar(urlPara(orden.modo()), ruc,
                orden.emisor().usuarioSol(), credenciales.get().claveSol(), nombre + ".zip", zip);

        String claveCdr = null;
        if (respuesta.cdr() != null) {
            claveCdr = ClavesDelBus.cdr(ruc, nombre);
            bus.escribir(claveCdr, respuesta.cdr(), "application/zip");
        }
        EstadoSunat estado = respuesta.aceptado() ? EstadoSunat.ACEPTADO
                : respuesta.rechazado() ? EstadoSunat.RECHAZADO
                : EstadoSunat.ERROR_ENVIO;
        return new ResultadoDeEmision(orden.id(), orden.empresaId(), orden.operacion(), estado,
                respuesta.codigo(), respuesta.descripcion(), respuesta.notas(), claveXml, claveCdr,
                firmado.resumen(), null, null, reloj.instant());
    }

    /**
     * La comunicación de baja (doc 13 §6). Se construye y se firma igual que un
     * comprobante; lo que cambia es que SUNAT no contesta con la constancia,
     * sino con un ticket. El estado {@code EN_PROCESO} es la señal de que hay
     * que volver a preguntar, y el ticket viaja en {@code codigo}.
     */
    private ResultadoDeEmision enviarBaja(OrdenDeEmision orden) {
        String ruc = orden.emisor().ruc();
        Optional<Credenciales> credenciales = credencialesDe(ruc);
        if (credenciales.isEmpty()) {
            return fallo(orden, "SIN_CREDENCIALES",
                    "No hay certificado o credenciales cargados para el RUC " + ruc + ".", null, null);
        }
        CertificateDetails certificado;
        try {
            certificado = abrirCertificado(ruc, credenciales.get());
        } catch (FirmadorDeComprobante.CertificadoNoAbre e) {
            return fallo(orden, "CERTIFICADO_NO_ABRE", e.getMessage(), null, null);
        }

        String nombre = orden.baja().nombreDeArchivo(ruc);
        FirmadorDeComprobante.Firmado firmado =
                firmador.firmar(constructorDeBaja.construir(orden), certificado);
        String claveXml = ClavesDelBus.DOCUMENTOS + ruc + "/" + nombre + ".xml";
        bus.escribir(claveXml, firmado.xml(), "application/xml");

        byte[] zip = Empaquetador.comprimir(nombre + ".xml", firmado.xml());
        RespuestaSunat respuesta = sunat.enviarResumen(urlPara(orden.modo()), ruc,
                orden.emisor().usuarioSol(), credenciales.get().claveSol(), nombre + ".zip", zip);

        if (respuesta.tipo() == RespuestaSunat.Tipo.TICKET) {
            return new ResultadoDeEmision(orden.id(), orden.empresaId(), orden.operacion(),
                    EstadoSunat.EN_PROCESO, respuesta.codigo(), respuesta.descripcion(),
                    List.of(), claveXml, null, firmado.resumen(), null, null, reloj.instant());
        }
        EstadoSunat estado = respuesta.rechazado() ? EstadoSunat.RECHAZADO : EstadoSunat.ERROR_ENVIO;
        return new ResultadoDeEmision(orden.id(), orden.empresaId(), orden.operacion(), estado,
                respuesta.codigo(), respuesta.descripcion(), respuesta.notas(), claveXml, null,
                firmado.resumen(), null, null, reloj.instant());
    }

    /**
     * Preguntar por un ticket. No firma nada ni construye XML: solo llama y
     * traduce. Si SUNAT sigue procesando ({@code 98}), el resultado lo dice y
     * quien consulte volverá más tarde.
     */
    private ResultadoDeEmision consultarTicket(OrdenDeEmision orden) {
        String ruc = orden.emisor().ruc();
        Optional<Credenciales> credenciales = credencialesDe(ruc);
        if (credenciales.isEmpty()) {
            return fallo(orden, "SIN_CREDENCIALES",
                    "No hay credenciales cargadas para el RUC " + ruc + ".", null, null);
        }
        String ticket = orden.consulta() == null ? null : orden.consulta().ticket();
        if (ticket == null || ticket.isBlank()) {
            return fallo(orden, "SIN_TICKET", "La orden de consulta no trae ticket.", null, null);
        }

        RespuestaSunat respuesta = sunat.consultarTicket(urlPara(orden.modo()), ruc,
                orden.emisor().usuarioSol(), credenciales.get().claveSol(), ticket);

        if (respuesta.tipo() == RespuestaSunat.Tipo.EN_PROCESO) {
            return new ResultadoDeEmision(orden.id(), orden.empresaId(), orden.operacion(),
                    EstadoSunat.EN_PROCESO, ticket, respuesta.descripcion(), List.of(), null, null,
                    null, null, null, reloj.instant());
        }
        String claveCdr = null;
        if (respuesta.cdr() != null) {
            claveCdr = ClavesDelBus.cdrDeTicket(ruc, ticket);
            bus.escribir(claveCdr, respuesta.cdr(), "application/zip");
        }
        EstadoSunat estado = respuesta.aceptado() ? EstadoSunat.ACEPTADO
                : respuesta.rechazado() ? EstadoSunat.RECHAZADO
                : EstadoSunat.ERROR_ENVIO;
        return new ResultadoDeEmision(orden.id(), orden.empresaId(), orden.operacion(), estado,
                respuesta.codigo(), respuesta.descripcion(), respuesta.notas(), null, claveCdr,
                null, null, null, reloj.instant());
    }

    private ResultadoDeEmision verificar(OrdenDeEmision orden) {
        String ruc = orden.emisor().ruc();
        Optional<Credenciales> credenciales = credencialesDe(ruc);
        if (credenciales.isEmpty()) {
            return fallo(orden, "SIN_CREDENCIALES",
                    "No hay certificado o credenciales cargados para el RUC " + ruc + ".", null, null);
        }
        try {
            var datos = FirmadorDeComprobante.describir(abrirCertificado(ruc, credenciales.get()));
            return new ResultadoDeEmision(orden.id(), orden.empresaId(), orden.operacion(),
                    EstadoSunat.ACEPTADO, "0", "El certificado abre con su contraseña.", List.of(),
                    null, null, null, datos.sujeto(), datos.venceEn(), reloj.instant());
        } catch (FirmadorDeComprobante.CertificadoNoAbre e) {
            return fallo(orden, "CERTIFICADO_NO_ABRE", e.getMessage(), null, null);
        }
    }

    private Optional<Credenciales> credencialesDe(String ruc) {
        return bus.leer(ClavesDelBus.credenciales(ruc))
                .map(bytes -> json.readValue(bytes, Credenciales.class))
                .filter(Credenciales::completas);
    }

    private CertificateDetails abrirCertificado(String ruc, Credenciales credenciales) {
        byte[] pfx = bus.leer(ClavesDelBus.certificado(ruc))
                .orElseThrow(() -> new FirmadorDeComprobante.CertificadoNoAbre(
                        "No hay certificado cargado para el RUC " + ruc + ".", null));
        return FirmadorDeComprobante.abrir(pfx, credenciales.claveCertificado());
    }

    private String urlPara(ModoSunat modo) {
        return modo == ModoSunat.PRODUCCION ? propiedades.urlProduccion() : propiedades.urlBeta();
    }

    private ResultadoDeEmision fallo(OrdenDeEmision orden, String codigo, String descripcion,
            String claveXml, String resumen) {
        return new ResultadoDeEmision(orden.id(), orden.empresaId(), orden.operacion(),
                EstadoSunat.ERROR_ENVIO, codigo, descripcion, List.of(), claveXml, null, resumen,
                null, null, reloj.instant());
    }

    static String texto(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
