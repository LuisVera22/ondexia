package com.ondexia.infrastructure.entrada.web.ventas;

import com.ondexia.application.emision.ComunicacionesDeBaja;
import com.ondexia.domain.comprobante.ComunicacionDeBaja;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * La comunicación de baja de facturas (doc 13 §6).
 *
 * <p>La respuesta de SUNAT no llega en la misma llamada: el envío devuelve un
 * ticket y el veredicto se consulta después. Por eso el listado sincroniza al
 * abrirse y la ficha vuelve a preguntar mientras la comunicación esté en curso,
 * igual que el estado de un comprobante.
 */
@RestController
@RequestMapping("/api/v1/ventas/comunicaciones-de-baja")
@Tag(name = "Comunicaciones de baja", description = "Facturas que se comunican a SUNAT como no emitidas")
public class ComunicacionDeBajaController {

    private final ComunicacionesDeBaja comunicaciones;

    public ComunicacionDeBajaController(ComunicacionesDeBaja comunicaciones) {
        this.comunicaciones = comunicaciones;
    }

    @Operation(
            summary = "Comunica a SUNAT que unas facturas no debieron existir",
            description = """
                    Todas del mismo día y dentro del plazo (hasta el 7.º día del mes siguiente al \
                    de emisión). SUNAT devuelve un ticket y el veredicto llega después; los \
                    comprobantes pasan a ANULADO cuando lo acepta.""")
    @RequierePermiso(modulo = "ventas.comunicacion_baja", accion = "enviar")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaComunicacion crear(@Valid @RequestBody PeticionBaja peticion) {
        return RespuestaComunicacion.desde(comunicaciones.crear(peticion.comprobantes().stream()
                .map(c -> new ComunicacionesDeBaja.ComprobanteAAnular(c.documentoId(), c.motivo()))
                .toList()));
    }

    @Operation(
            summary = "Las últimas comunicaciones de baja",
            description = "Antes de responder pregunta por los tickets que lleven esperando.")
    @RequierePermiso(modulo = "ventas.comunicacion_baja", accion = "consultar")
    @GetMapping
    public List<RespuestaComunicacion> recientes() {
        comunicaciones.sincronizarPendientes();
        return comunicaciones.recientes().stream().map(RespuestaComunicacion::desde).toList();
    }

    @Operation(summary = "Una comunicación de baja, con sus comprobantes")
    @RequierePermiso(modulo = "ventas.comunicacion_baja", accion = "consultar")
    @GetMapping("/{id}")
    public RespuestaComunicacion una(@PathVariable UUID id) {
        comunicaciones.sincronizarPendientes();
        return RespuestaComunicacion.desde(comunicaciones.obtener(id));
    }

    @Operation(
            summary = "Vuelve a enviar una comunicación rechazada o con error",
            description = """
                    Con un correlativo nuevo del día. Una que ya tiene ticket no se reenvía: lo \
                    que falta es consultarlo.""")
    @RequierePermiso(modulo = "ventas.comunicacion_baja", accion = "enviar")
    @PostMapping("/{id}/reintento")
    public RespuestaComunicacion reintentar(@PathVariable UUID id) {
        return RespuestaComunicacion.desde(comunicaciones.reintentar(id));
    }

    @Operation(summary = "URL temporal del XML firmado")
    @RequierePermiso(modulo = "ventas.comunicacion_baja", accion = "consultar")
    @GetMapping("/{id}/xml")
    public ComprobanteElectronicoController.RespuestaDescarga xml(@PathVariable UUID id) {
        return new ComprobanteElectronicoController.RespuestaDescarga(comunicaciones.urlDelXml(id));
    }

    @Operation(summary = "URL temporal del CDR que devolvió SUNAT")
    @RequierePermiso(modulo = "ventas.comunicacion_baja", accion = "consultar")
    @GetMapping("/{id}/cdr")
    public ComprobanteElectronicoController.RespuestaDescarga cdr(@PathVariable UUID id) {
        return new ComprobanteElectronicoController.RespuestaDescarga(comunicaciones.urlDelCdr(id));
    }

    // ── Cuerpos ─────────────────────────────────────────────────────────────

    public record ComprobantePedido(
            @NotNull(message = "Indica el comprobante.") UUID documentoId,
            @jakarta.validation.constraints.NotBlank(message = "Indica por qué no debió existir.")
            @Size(max = 300)
            String motivo) {
    }

    public record PeticionBaja(
            @NotEmpty(message = "Elige al menos una factura.") @Valid
            List<ComprobantePedido> comprobantes) {
    }

    public record RespuestaComprobante(UUID documentoId, String tipo, String numeroCompleto,
            String motivo) {
    }

    /**
     * @param estado el de SUNAT: EN_COLA, EN_PROCESO, ACEPTADO, RECHAZADO, ERROR_ENVIO
     * @param diasDePlazo los que quedan para comunicar la baja; negativo si venció
     */
    public record RespuestaComunicacion(UUID id, String identificador, LocalDate fechaComprobantes,
            LocalDate fechaGeneracion, String estado, int intentos, String ticket, String codigo,
            String descripcion, Instant encoladaEn, Instant respondidaEn, boolean xmlDisponible,
            boolean cdrDisponible, boolean admiteReintento, long diasDePlazo,
            List<RespuestaComprobante> comprobantes) {

        static RespuestaComunicacion desde(ComunicacionDeBaja c) {
            return new RespuestaComunicacion(c.id(), c.identificador(), c.fechaDeLosComprobantes(),
                    c.fechaDeGeneracion(), c.estado().name(), c.intentos(), c.ticket(),
                    c.codigoSunat(), c.descripcionSunat(), c.encoladaEn(), c.respondidaEn(),
                    c.claveXml() != null, c.claveCdr() != null, c.estado().admiteReintento(),
                    c.diasDePlazoDesde(LocalDate.now(java.time.ZoneId.of("America/Lima"))),
                    c.comprobantes().stream()
                            .map(r -> new RespuestaComprobante(r.documentoId(), r.tipo().codigo(),
                                    r.numeroCompleto(), r.motivo()))
                            .toList());
        }
    }
}
