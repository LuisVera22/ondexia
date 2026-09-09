package com.ondexia.infrastructure.entrada.web.panel;

import com.ondexia.application.panel.PanelDelDia;
import com.ondexia.domain.comprobante.ComprobanteElectronico;
import com.ondexia.domain.ventas.SesionCaja;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La portada.
 *
 * <p><strong>Sin {@code @RequierePermiso}, y es deliberado.</strong> Es la
 * primera pantalla de cualquiera que entre, incluido quien solo tenga
 * Configuración; exigir un permiso de ventas para verla convertiría la portada
 * en un 403. Lo que decide qué se devuelve es {@link PanelDelDia}, bloque a
 * bloque: lo que el usuario no puede consultar llega como {@code null}, no como
 * cero. La sesión sí es obligatoria, como en toda la API.
 */
@RestController
@RequestMapping("/api/v1/panel")
@Tag(name = "Panel", description = "Las cifras del día en la portada")
public class PanelController {

    private final PanelDelDia panel;

    public PanelController(PanelDelDia panel) {
        this.panel = panel;
    }

    @Operation(summary = "Estado de las cajas, ventas del día y comprobantes por atender")
    @GetMapping
    public Respuesta consultar() {
        return Respuesta.desde(panel.consultar());
    }

    /**
     * @param cajasAbiertas null si el usuario no consulta cajas
     * @param ventas null si no consulta ventas
     * @param comprobantesPorAtender null si no consulta comprobantes
     */
    public record Respuesta(LocalDate fecha, List<RespuestaCaja> cajasAbiertas,
            RespuestaVentas ventas, RespuestaPorAtender comprobantesPorAtender) {

        static Respuesta desde(PanelDelDia.Resumen resumen) {
            return new Respuesta(
                    resumen.fecha(),
                    resumen.cajasAbiertas() == null ? null
                            : resumen.cajasAbiertas().stream().map(RespuestaCaja::desde).toList(),
                    resumen.ventas() == null ? null
                            : new RespuestaVentas(resumen.ventas().documentos(),
                                    resumen.ventas().importe()),
                    resumen.cuantosPorAtender() == null ? null
                            : new RespuestaPorAtender(resumen.cuantosPorAtender(),
                                    resumen.primeros().stream()
                                            .map(RespuestaComprobante::desde).toList()));
        }
    }

    public record RespuestaCaja(UUID sesionId, UUID cajaId, Instant abiertaEn,
            BigDecimal montoInicial) {

        static RespuestaCaja desde(SesionCaja sesion) {
            return new RespuestaCaja(sesion.id(), sesion.cajaId(), sesion.abiertaEn(),
                    sesion.montoInicial());
        }
    }

    public record RespuestaVentas(int documentos, BigDecimal importe) {
    }

    /** @param total puede ser mayor que {@code primeros}: la lista se recorta. */
    public record RespuestaPorAtender(int total, List<RespuestaComprobante> primeros) {
    }

    /**
     * @param tipo el código del catálogo 01 —{@code 01}, {@code 03}, {@code 07}—
     *     y no el nombre de la constante: es lo que llevan el resto de las
     *     respuestas de ventas y lo que la ruta del documento espera. El nombre
     *     legible viaja aparte para que la pantalla no tenga que traducirlo.
     */
    public record RespuestaComprobante(UUID documentoId, String tipo, String tipoNombre,
            String serie, long numero, String numeroCompleto, String estado, String codigoSunat,
            String descripcionSunat) {

        static RespuestaComprobante desde(ComprobanteElectronico c) {
            return new RespuestaComprobante(c.documentoId(), c.tipo().codigo(), c.tipo().nombre(),
                    c.serie(), c.numero(),
                    "%s-%08d".formatted(c.serie(), c.numero()),
                    c.estado().name(), c.codigoSunat(), c.descripcionSunat());
        }
    }
}
