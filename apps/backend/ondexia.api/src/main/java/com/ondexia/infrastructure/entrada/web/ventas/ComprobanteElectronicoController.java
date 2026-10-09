package com.ondexia.infrastructure.entrada.web.ventas;

import com.ondexia.application.emision.EmisionElectronica;
import com.ondexia.domain.comprobante.ComprobanteElectronico;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que SUNAT dijo de un comprobante, y volver a intentarlo (doc 14 §3).
 *
 * <p>Cuelga del documento, no del comprobante electrónico: la pantalla tiene el
 * identificador del documento y no tiene por qué conocer el otro.
 */
@RestController
@RequestMapping("/api/v1/ventas/comprobantes/{documentoId}/sunat")
@Tag(name = "Envío a SUNAT", description = "Estado del comprobante ante SUNAT, XML firmado y CDR")
public class ComprobanteElectronicoController {

    private final EmisionElectronica emision;

    public ComprobanteElectronicoController(EmisionElectronica emision) {
        this.emision = emision;
    }

    @Operation(
            summary = "Estado del comprobante ante SUNAT",
            description = """
                    Si sigue en cola, mira si el Emisor ya dejó resultado y lo aplica. La \
                    pantalla lo consulta cada pocos segundos mientras espera.""")
    @RequierePermiso(modulo = "ventas.comprobante", accion = "consultar")
    @GetMapping
    public RespuestaSunat estado(@PathVariable UUID documentoId) {
        return RespuestaSunat.desde(emision.estadoDe(documentoId));
    }

    @Operation(
            summary = "Vuelve a enviar un comprobante rechazado o con error de envío",
            description = """
                    Con el mismo número: para SUNAT un comprobante rechazado no existe. Un \
                    comprobante en cola solo se reintenta pasados diez minutos sin respuesta; \
                    uno aceptado, nunca.""")
    @RequierePermiso(modulo = "ventas.comprobante", accion = "emitir")
    @PostMapping("/reintento")
    public RespuestaSunat reintentar(@PathVariable UUID documentoId) {
        return RespuestaSunat.desde(emision.reintentar(documentoId));
    }

    @Operation(summary = "URL temporal del XML firmado")
    @RequierePermiso(modulo = "ventas.comprobante", accion = "consultar")
    @GetMapping("/xml")
    public RespuestaDescarga xml(@PathVariable UUID documentoId) {
        return new RespuestaDescarga(emision.urlDelXml(documentoId));
    }

    @Operation(summary = "URL temporal del CDR que devolvió SUNAT")
    @RequierePermiso(modulo = "ventas.comprobante", accion = "consultar")
    @GetMapping("/cdr")
    public RespuestaDescarga cdr(@PathVariable UUID documentoId) {
        return new RespuestaDescarga(emision.urlDelCdr(documentoId));
    }

    public record RespuestaDescarga(String url) {
    }

    public record RespuestaSunat(UUID comprobanteId, UUID documentoId, String estado, int intentos,
            Instant encoladoEn, Instant respondidoEn, String codigo, String descripcion,
            List<String> observaciones, boolean xmlDisponible, boolean cdrDisponible,
            String resumenFirma, boolean admiteReintento) {

        static RespuestaSunat desde(ComprobanteElectronico c) {
            return new RespuestaSunat(c.id(), c.documentoId(), c.estado().name(), c.intentos(),
                    c.encoladoEn(), c.respondidoEn(), c.codigoSunat(), c.descripcionSunat(),
                    c.observaciones(), c.claveXml() != null, c.claveCdr() != null, c.resumenFirma(),
                    c.estado().admiteReintento());
        }
    }
}
