package com.ondexia.infrastructure.entrada.web.ventas;

import com.ondexia.application.ventas.Cajas;
import com.ondexia.application.ventas.SesionesDeCaja;
import com.ondexia.domain.ventas.Caja;
import com.ondexia.domain.ventas.FormaDePago;
import com.ondexia.domain.ventas.SesionCaja;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ventas/cajas")
@Tag(name = "Cajas", description = "Puntos de cobro por establecimiento y sus turnos")
public class CajaController {

    private final Cajas cajas;
    private final SesionesDeCaja sesiones;

    public CajaController(Cajas cajas, SesionesDeCaja sesiones) {
        this.cajas = cajas;
        this.sesiones = sesiones;
    }

    @Operation(summary = "Lista las cajas que el usuario alcanza, con su sesión abierta si la hay")
    @RequierePermiso(modulo = "ventas.caja", accion = "consultar")
    @GetMapping
    public List<RespuestaCaja> listar() {
        var abiertas = sesiones.abiertas();
        return cajas.listar().stream()
                .map(caja -> RespuestaCaja.desde(caja, abiertas.stream()
                        .filter(s -> s.cajaId().equals(caja.id())).findFirst().orElse(null)))
                .toList();
    }

    @Operation(
            summary = "Registra una caja en un establecimiento",
            description = "El código es único por establecimiento y no cambia después.")
    @RequierePermiso(modulo = "ventas.caja", accion = "registrar")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaCaja registrar(@Valid @RequestBody PeticionNueva peticion) {
        return RespuestaCaja.desde(
                cajas.registrar(peticion.codigo(), peticion.nombre(), peticion.sucursalId()), null);
    }

    @Operation(summary = "Cambia el nombre de la caja")
    @RequierePermiso(modulo = "ventas.caja", accion = "editar")
    @PutMapping("/{id}")
    public RespuestaCaja renombrar(@PathVariable UUID id, @Valid @RequestBody PeticionEdicion peticion) {
        return RespuestaCaja.desde(cajas.renombrar(id, peticion.nombre()),
                sesiones.abiertaDe(id).orElse(null));
    }

    @Operation(
            summary = "Activa o desactiva una caja",
            description = "No se desactiva con una sesión abierta. Nunca se borra: sus sesiones la referencian.")
    @RequierePermiso(modulo = "ventas.caja", accion = "desactivar")
    @PutMapping("/{id}/estado")
    public RespuestaCaja cambiarEstado(@PathVariable UUID id, @Valid @RequestBody PeticionEstado peticion) {
        return RespuestaCaja.desde(cajas.cambiarEstado(id, peticion.activa()),
                sesiones.abiertaDe(id).orElse(null));
    }

    // ── Sesiones ────────────────────────────────────────────────────────────

    @Operation(summary = "La sesión abierta de la caja, o 204 si está cerrada")
    @RequierePermiso(modulo = "ventas.caja", accion = "consultar")
    @GetMapping("/{id}/sesion-abierta")
    public ResponseEntity<RespuestaSesion> sesionAbierta(@PathVariable UUID id) {
        return sesiones.abiertaDe(id)
                .map(sesion -> ResponseEntity.ok(RespuestaSesion.desde(sesion)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Historial de sesiones de la caja, de la más reciente a la más antigua")
    @RequierePermiso(modulo = "ventas.caja", accion = "consultar")
    @GetMapping("/{id}/sesiones")
    public List<RespuestaSesion> historial(@PathVariable UUID id) {
        return sesiones.historialDe(id).stream().map(RespuestaSesion::desde).toList();
    }

    @Operation(
            summary = "Abre la caja",
            description = "Con el monto inicial del cajón. Responde 400 si ya hay una sesión abierta.")
    @RequierePermiso(modulo = "ventas.caja", accion = "abrir")
    @PostMapping("/{id}/sesiones")
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaSesion abrir(@PathVariable UUID id, @Valid @RequestBody PeticionApertura peticion) {
        return RespuestaSesion.desde(sesiones.abrir(id, peticion.montoInicial()));
    }

    @Operation(
            summary = "Cierra la sesión con el arqueo",
            description = """
                    Lo declarado por forma de pago; lo que no se declare vale cero. La diferencia \
                    contra lo calculado se guarda, no se corrige. Una sesión cerrada no se modifica.""")
    @RequierePermiso(modulo = "ventas.caja", accion = "cerrar")
    @PutMapping("/sesiones/{sesionId}/cierre")
    public RespuestaSesion cerrar(@PathVariable UUID sesionId, @Valid @RequestBody PeticionCierre peticion) {
        return RespuestaSesion.desde(sesiones.cerrar(sesionId, peticion.declaradoComoMapa()));
    }

    // ── Cuerpos ─────────────────────────────────────────────────────────────

    public record PeticionNueva(
            @NotBlank(message = "El código es obligatorio.")
            @Pattern(regexp = "[A-Za-z0-9-]{1,20}",
                    message = "El código admite letras, números y guiones, hasta 20 caracteres.")
            String codigo,

            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 200)
            String nombre,

            @NotNull(message = "La caja tiene que pertenecer a un establecimiento.")
            UUID sucursalId) {
    }

    public record PeticionEdicion(
            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 200)
            String nombre) {
    }

    public record PeticionEstado(
            @NotNull(message = "Indica si la caja queda activa.")
            Boolean activa) {
    }

    public record PeticionApertura(
            @NotNull(message = "Indica el monto inicial, aunque sea cero.")
            @DecimalMin(value = "0", message = "El monto inicial no puede ser negativo.")
            @Digits(integer = 12, fraction = 6, message = "Hasta seis decimales.")
            BigDecimal montoInicial) {
    }

    /** Lo declarado por forma de pago. Las formas ausentes valen cero. */
    public record PeticionCierre(Map<String, BigDecimal> declarado) {

        Map<FormaDePago, BigDecimal> declaradoComoMapa() {
            var mapa = new EnumMap<FormaDePago, BigDecimal>(FormaDePago.class);
            if (declarado != null) {
                declarado.forEach((clave, importe) -> mapa.put(FormaDePago.valueOf(clave), importe));
            }
            return mapa;
        }
    }

    public record RespuestaCaja(UUID id, UUID sucursalId, String codigo, String nombre,
            boolean activa, RespuestaSesion sesionAbierta) {

        static RespuestaCaja desde(Caja caja, SesionCaja abierta) {
            return new RespuestaCaja(caja.id(), caja.sucursalId(), caja.codigo(), caja.nombre(),
                    caja.estaActiva(), abierta == null ? null : RespuestaSesion.desde(abierta));
        }
    }

    public record RespuestaSesion(UUID id, UUID cajaId, String estado, UUID abiertaPor,
            Instant abiertaEn, BigDecimal montoInicial, UUID cerradaPor, Instant cerradaEn,
            Map<String, BigDecimal> declarado, Map<String, BigDecimal> calculado,
            Map<String, BigDecimal> diferencia) {

        static RespuestaSesion desde(SesionCaja sesion) {
            return new RespuestaSesion(sesion.id(), sesion.cajaId(), sesion.estado().name(),
                    sesion.abiertaPor(), sesion.abiertaEn(), sesion.montoInicial(),
                    sesion.cerradaPor(), sesion.cerradaEn(),
                    porNombre(sesion.declarado()), porNombre(sesion.calculado()),
                    sesion.estaAbierta() ? Map.of() : porNombre(sesion.diferencia()));
        }

        private static Map<String, BigDecimal> porNombre(Map<FormaDePago, BigDecimal> mapa) {
            var json = new java.util.LinkedHashMap<String, BigDecimal>();
            for (FormaDePago forma : FormaDePago.values()) {
                if (mapa.containsKey(forma)) {
                    json.put(forma.name(), mapa.get(forma));
                }
            }
            return json;
        }
    }
}
