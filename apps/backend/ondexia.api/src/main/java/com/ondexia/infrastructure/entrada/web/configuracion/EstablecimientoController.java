package com.ondexia.infrastructure.entrada.web.configuracion;

import com.ondexia.application.configuracion.Establecimientos;
import com.ondexia.domain.identidad.Sucursal;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Establecimientos anexos de la empresa activa.
 *
 * <p>Sin paginación: un contribuyente peruano tiene entre uno y unas decenas de
 * anexos, y son de los que se ven todos a la vez para elegir. Añadirla sería
 * complicar el cliente para un caso que no ocurre — cuando ocurra, se añade.
 */
@RestController
@RequestMapping("/api/v1/configuracion/establecimientos")
@Tag(name = "Establecimientos", description = "Anexos de la empresa ante SUNAT")
public class EstablecimientoController {

    private final Establecimientos establecimientos;

    public EstablecimientoController(Establecimientos establecimientos) {
        this.establecimientos = establecimientos;
    }

    @Operation(summary = "Lista los establecimientos, activos e inactivos")
    @RequierePermiso(modulo = "configuracion.sucursal", accion = "consultar")
    @GetMapping
    public List<RespuestaEstablecimiento> listar() {
        return establecimientos.listar().stream().map(RespuestaEstablecimiento::desde).toList();
    }

    @Operation(
            summary = "Registra un establecimiento",
            description = """
                    El código son los cuatro dígitos que asigna SUNAT y aparecen en la ficha \
                    RUC; no se genera aquí. La casa matriz es 0000.""")
    @RequierePermiso(modulo = "configuracion.sucursal", accion = "registrar")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaEstablecimiento registrar(@Valid @RequestBody PeticionNuevo peticion) {
        return RespuestaEstablecimiento.desde(establecimientos.registrar(
                peticion.codigo(), peticion.nombre(), peticion.direccion(), peticion.ubigeo()));
    }

    @Operation(
            summary = "Actualiza un establecimiento",
            description = "El código no se puede cambiar: identifica al anexo ante SUNAT.")
    @RequierePermiso(modulo = "configuracion.sucursal", accion = "editar")
    @PutMapping("/{id}")
    public RespuestaEstablecimiento actualizar(
            @PathVariable UUID id, @Valid @RequestBody PeticionEdicion peticion) {
        return RespuestaEstablecimiento.desde(establecimientos.actualizar(
                id, peticion.nombre(), peticion.direccion(), peticion.ubigeo()));
    }

    @Operation(
            summary = "Activa o desactiva un establecimiento",
            description = """
                    Nunca borra. Un establecimiento aparece en los comprobantes ya emitidos: \
                    borrarlo dejaria documentos apuntando a nada. Desactivarlo lo saca de los \
                    desplegables y conserva su codigo, sus series y su numeracion, de modo que \
                    reactivarlo lo devuelve tal como estaba.""")
    @RequierePermiso(modulo = "configuracion.sucursal", accion = "desactivar")
    @PutMapping("/{id}/estado")
    public RespuestaEstablecimiento cambiarEstado(
            @PathVariable UUID id, @Valid @RequestBody PeticionEstado peticion) {
        return RespuestaEstablecimiento.desde(
                establecimientos.cambiarEstado(id, peticion.activa()));
    }

    /**
     * Sustituye al {@code DELETE} que habia.
     *
     * <p>Era un camino de ida: desactivado, el establecimiento no se podia
     * recuperar desde ninguna capa — ni la API lo ofrecia, ni el dominio tenia un
     * {@code activar}. Un {@code PUT} sobre el estado dice lo que de verdad
     * pasa, va en los dos sentidos, y es como ya funcionaban las series y los
     * usuarios.
     */
    public record PeticionEstado(
            @NotNull(message = "Indica si el establecimiento queda activo.")
            Boolean activa) {
    }

    public record PeticionNuevo(
            @NotBlank(message = "El código es obligatorio.")
            @Pattern(regexp = "\\d{4}", message = "El código son cuatro dígitos, como 0000.")
            String codigo,

            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 200)
            String nombre,

            // Obligatoria: la columna es NOT NULL (V1). Sin esta anotación el
            // fallo llega desde la base como un 409 genérico que no dice qué
            // campo falta.
            @NotBlank(message = "La dirección es obligatoria.")
            @Size(max = 400)
            String direccion,

            @Pattern(regexp = "^$|^\\d{6}$", message = "El ubigeo son seis dígitos.")
            String ubigeo) {
    }

    public record PeticionEdicion(
            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 200)
            String nombre,

            // Obligatoria: la columna es NOT NULL (V1). Sin esta anotación el
            // fallo llega desde la base como un 409 genérico que no dice qué
            // campo falta.
            @NotBlank(message = "La dirección es obligatoria.")
            @Size(max = 400)
            String direccion,

            @Pattern(regexp = "^$|^\\d{6}$", message = "El ubigeo son seis dígitos.")
            String ubigeo) {
    }

    public record RespuestaEstablecimiento(
            UUID id,
            String codigo,
            String nombre,
            String direccion,
            String ubigeo,
            boolean activa) {

        static RespuestaEstablecimiento desde(Sucursal sucursal) {
            return new RespuestaEstablecimiento(
                    sucursal.id(),
                    sucursal.codigo(),
                    sucursal.nombre(),
                    sucursal.direccion(),
                    sucursal.ubigeo() == null ? null : sucursal.ubigeo().valor(),
                    sucursal.estaActiva());
        }
    }
}
