package com.ondexia.infrastructure.entrada.web.almacen;

import com.ondexia.application.almacen.Almacenes;
import com.ondexia.domain.almacen.Almacen;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Almacenes: dónde están físicamente las existencias.
 *
 * <p>Vive bajo {@code /almacen} y no bajo {@code /configuracion} porque el
 * permiso que lo gobierna es {@code almacen.almacen}: quien administra el
 * inventario los crea, sin necesitar acceso a los datos fiscales de la empresa.
 */
@RestController
@RequestMapping("/api/v1/almacen/almacenes")
@Tag(name = "Almacenes", description = "Ubicaciones físicas de las existencias")
public class AlmacenController {

    private final Almacenes almacenes;

    public AlmacenController(Almacenes almacenes) {
        this.almacenes = almacenes;
    }

    @Operation(summary = "Lista los almacenes, activos e inactivos")
    @RequierePermiso(modulo = "almacen.almacen", accion = "consultar")
    @GetMapping
    public List<RespuestaAlmacen> listar() {
        return almacenes.listar().stream().map(RespuestaAlmacen::desde).toList();
    }

    @Operation(
            summary = "Registra un almacén",
            description = """
                    El código lo elige el cliente: se teclea en cada movimiento de mercadería, \
                    así que conviene que sea corto. Se guarda en mayúsculas.""")
    @RequierePermiso(modulo = "almacen.almacen", accion = "registrar")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaAlmacen registrar(@Valid @RequestBody PeticionNuevo peticion) {
        return RespuestaAlmacen.desde(
                almacenes.registrar(peticion.codigo(), peticion.nombre(), peticion.sucursalId()));
    }

    @Operation(
            summary = "Actualiza un almacén",
            description = "El código no cambia: identifica al almacén en los movimientos ya registrados.")
    @RequierePermiso(modulo = "almacen.almacen", accion = "editar")
    @PutMapping("/{id}")
    public RespuestaAlmacen actualizar(
            @PathVariable UUID id, @Valid @RequestBody PeticionEdicion peticion) {
        return RespuestaAlmacen.desde(
                almacenes.actualizar(id, peticion.nombre(), peticion.sucursalId()));
    }

    @Operation(
            summary = "Desactiva un almacén",
            description = "No lo borra: aparece en cada movimiento de stock que lo tocó.")
    @RequierePermiso(modulo = "almacen.almacen", accion = "desactivar")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable UUID id) {
        almacenes.desactivar(id);
    }

    /**
     * @param sucursalId opcional: hay almacenes que no cuelgan de ningún
     *                   establecimiento
     */
    public record PeticionNuevo(
            @NotBlank(message = "El código es obligatorio.")
            @Pattern(regexp = "[A-Za-z0-9-]{1,20}",
                    message = "El código admite letras, números y guiones, hasta 20 caracteres.")
            String codigo,

            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 200)
            String nombre,

            UUID sucursalId) {
    }

    public record PeticionEdicion(
            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 200)
            String nombre,

            UUID sucursalId) {
    }

    public record RespuestaAlmacen(
            UUID id, String codigo, String nombre, UUID sucursalId, boolean activo) {

        static RespuestaAlmacen desde(Almacen almacen) {
            return new RespuestaAlmacen(
                    almacen.id(),
                    almacen.codigo(),
                    almacen.nombre(),
                    almacen.sucursalId(),
                    almacen.estaActivo());
        }
    }
}
