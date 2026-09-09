package com.ondexia.infrastructure.entrada.web.almacen;

import com.ondexia.application.almacen.Existencias;
import com.ondexia.application.almacen.Productos;
import com.ondexia.domain.almacen.AfectacionIgv;
import com.ondexia.domain.almacen.DisponibilidadEnLocal;
import com.ondexia.domain.almacen.Existencia;
import com.ondexia.domain.almacen.MovimientoStock;
import com.ondexia.domain.almacen.Producto;
import com.ondexia.domain.almacen.UnidadDeMedida;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/almacen/productos")
@Tag(name = "Productos", description = "Catálogo de la empresa, disponibilidad por local y existencias")
public class ProductoController {

    private final Productos productos;
    private final Existencias existencias;

    public ProductoController(Productos productos, Existencias existencias) {
        this.productos = productos;
        this.existencias = existencias;
    }

    @Operation(summary = "Lista o busca productos", description = "Sin `q`, todo el catálogo. Con `q`, por código o nombre, hasta 50.")
    @RequierePermiso(modulo = "almacen.producto", accion = "consultar")
    @GetMapping
    public List<RespuestaProducto> listar(@RequestParam(required = false) String q) {
        return productos.buscar(q).stream().map(RespuestaProducto::desde).toList();
    }

    @Operation(
            summary = "El catálogo del establecimiento para el mostrador",
            description = "Activos y disponibles en el local, con el precio que rige ahí y lo que hay en su almacén. Hasta 50.")
    @RequierePermiso(modulo = "almacen.producto", accion = "consultar")
    @GetMapping("/disponibles")
    public List<RespuestaDisponible> disponibles(@RequestParam UUID sucursalId,
            @RequestParam(required = false) String q) {
        return productos.disponiblesEn(sucursalId, q).stream().map(RespuestaDisponible::desde).toList();
    }

    @Operation(summary = "Los catálogos de SUNAT que admite el producto: unidades (03) y afectaciones (07)")
    @RequierePermiso(modulo = "almacen.producto", accion = "consultar")
    @GetMapping("/catalogos")
    public RespuestaCatalogos catalogos() {
        return new RespuestaCatalogos(
                Arrays.stream(UnidadDeMedida.values())
                        .map(u -> new Opcion(u.codigo(), u.nombre())).toList(),
                Arrays.stream(AfectacionIgv.values())
                        .map(a -> new Opcion(a.name(), a.nombre())).toList());
    }

    @Operation(summary = "Ficha de un producto")
    @RequierePermiso(modulo = "almacen.producto", accion = "consultar")
    @GetMapping("/{id}")
    public RespuestaProducto obtener(@PathVariable UUID id) {
        return RespuestaProducto.desde(productos.obtener(id));
    }

    @Operation(summary = "Registra un producto, disponible en el establecimiento indicado")
    @RequierePermiso(modulo = "almacen.producto", accion = "registrar")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaProducto registrar(@Valid @RequestBody PeticionNuevo peticion) {
        return RespuestaProducto.desde(
                productos.registrar(peticion.codigo(), peticion.datos(), peticion.sucursalId()));
    }

    @Operation(summary = "Actualiza la ficha. El código no cambia")
    @RequierePermiso(modulo = "almacen.producto", accion = "editar")
    @PutMapping("/{id}")
    public RespuestaProducto actualizar(@PathVariable UUID id, @Valid @RequestBody PeticionEdicion peticion) {
        return RespuestaProducto.desde(productos.actualizar(id, peticion.datos()));
    }

    @Operation(summary = "Activa o desactiva. Nunca se borra: los comprobantes lo referencian")
    @RequierePermiso(modulo = "almacen.producto", accion = "desactivar")
    @PutMapping("/{id}/estado")
    public RespuestaProducto cambiarEstado(@PathVariable UUID id, @Valid @RequestBody PeticionEstado peticion) {
        return RespuestaProducto.desde(productos.cambiarEstado(id, peticion.activo()));
    }

    // ── Disponibilidad por local ────────────────────────────────────────────

    @Operation(summary = "En qué establecimientos se ofrece y a qué precio", description = "Un establecimiento sin fila no ofrece el producto.")
    @RequierePermiso(modulo = "almacen.producto", accion = "consultar")
    @GetMapping("/{id}/locales")
    public List<RespuestaDisponibilidad> disponibilidad(@PathVariable UUID id) {
        return productos.disponibilidadDe(id).stream().map(RespuestaDisponibilidad::desde).toList();
    }

    @Operation(summary = "Fija si se ofrece en un establecimiento y su precio propio (null: el de lista)")
    @RequierePermiso(modulo = "almacen.producto", accion = "editar")
    @PutMapping("/{id}/locales/{sucursalId}")
    public RespuestaDisponibilidad fijarDisponibilidad(@PathVariable UUID id, @PathVariable UUID sucursalId,
            @Valid @RequestBody PeticionDisponibilidad peticion) {
        return RespuestaDisponibilidad.desde(
                productos.fijarDisponibilidad(id, sucursalId, peticion.disponible(), peticion.precio()));
    }

    // ── Existencias ─────────────────────────────────────────────────────────

    @Operation(summary = "Cuánto hay por almacén")
    @RequierePermiso(modulo = "almacen.stock", accion = "consultar")
    @GetMapping("/{id}/existencias")
    public List<RespuestaExistencia> existencias(@PathVariable UUID id) {
        return existencias.de(id).stream().map(RespuestaExistencia::desde).toList();
    }

    @Operation(summary = "El libro: los últimos movimientos del producto, del más reciente al más antiguo")
    @RequierePermiso(modulo = "almacen.stock", accion = "consultar")
    @GetMapping("/{id}/movimientos")
    public List<RespuestaMovimiento> movimientos(@PathVariable UUID id) {
        return existencias.movimientosDe(id).stream().map(RespuestaMovimiento::desde).toList();
    }

    @Operation(summary = "Ajusta por conteo", description = "Lo contado manda; la diferencia queda anotada como movimiento de ajuste.")
    @RequierePermiso(modulo = "almacen.stock", accion = "ajustar")
    @PostMapping("/{id}/existencias/ajustes")
    public RespuestaExistencia ajustar(@PathVariable UUID id, @Valid @RequestBody PeticionAjuste peticion) {
        return RespuestaExistencia.desde(
                existencias.ajustar(id, peticion.almacenId(), peticion.cantidad(), peticion.motivo()));
    }

    // ── Cuerpos ─────────────────────────────────────────────────────────────

    public record PeticionNuevo(
            @NotBlank(message = "El código es obligatorio.")
            @Pattern(regexp = "[A-Za-z0-9-]{1,30}", message = "Letras, números y guiones, hasta 30 caracteres.")
            String codigo,
            @NotBlank(message = "El nombre es obligatorio.") @Size(max = 300) String nombre,
            @Size(max = 2000) String descripcion,
            @NotNull(message = "Indica la unidad de medida.") UnidadDeMedida unidad,
            @NotNull(message = "Indica la afectación al IGV.") AfectacionIgv afectacion,
            @NotNull(message = "Indica el precio de lista, aunque sea cero.")
            @DecimalMin(value = "0", message = "El precio no puede ser negativo.")
            @Digits(integer = 12, fraction = 6, message = "Hasta seis decimales.")
            BigDecimal precioLista,
            Boolean controlaStock,
            @NotNull(message = "Indica el establecimiento en el que se ofrece.") UUID sucursalId) {

        Productos.Datos datos() {
            return new Productos.Datos(nombre, descripcion, unidad, afectacion, precioLista,
                    controlaStock == null || controlaStock);
        }
    }

    public record PeticionEdicion(
            @NotBlank(message = "El nombre es obligatorio.") @Size(max = 300) String nombre,
            @Size(max = 2000) String descripcion,
            @NotNull(message = "Indica la unidad de medida.") UnidadDeMedida unidad,
            @NotNull(message = "Indica la afectación al IGV.") AfectacionIgv afectacion,
            @NotNull(message = "Indica el precio de lista, aunque sea cero.")
            @DecimalMin(value = "0", message = "El precio no puede ser negativo.")
            @Digits(integer = 12, fraction = 6, message = "Hasta seis decimales.")
            BigDecimal precioLista,
            Boolean controlaStock) {

        Productos.Datos datos() {
            return new Productos.Datos(nombre, descripcion, unidad, afectacion, precioLista,
                    controlaStock == null || controlaStock);
        }
    }

    public record PeticionEstado(@NotNull(message = "Indica si el producto queda activo.") Boolean activo) {
    }

    public record PeticionDisponibilidad(
            @NotNull(message = "Indica si se ofrece en el establecimiento.") Boolean disponible,
            @DecimalMin(value = "0", message = "El precio no puede ser negativo.")
            @Digits(integer = 12, fraction = 6, message = "Hasta seis decimales.")
            BigDecimal precio) {
    }

    public record PeticionAjuste(
            @NotNull(message = "Indica el almacén.") UUID almacenId,
            @NotNull(message = "Indica la cantidad contada.")
            @DecimalMin(value = "0", message = "Lo contado no puede ser negativo.")
            @Digits(integer = 12, fraction = 6, message = "Hasta seis decimales.")
            BigDecimal cantidad,
            @Size(max = 500) String motivo) {
    }

    public record Opcion(String codigo, String nombre) {
    }

    public record RespuestaCatalogos(List<Opcion> unidades, List<Opcion> afectaciones) {
    }

    public record RespuestaProducto(UUID id, String codigo, String nombre, String descripcion,
            String unidad, String unidadNombre, String afectacion, String afectacionNombre,
            boolean llevaIgv, BigDecimal precioLista, boolean controlaStock, boolean activo) {

        static RespuestaProducto desde(Producto p) {
            return new RespuestaProducto(p.id(), p.codigo(), p.nombre(), p.descripcion(),
                    p.unidad().codigo(), p.unidad().nombre(), p.afectacion().name(),
                    p.afectacion().nombre(), p.afectacion().llevaIgv(), p.precioLista(),
                    p.controlaStock(), p.estaActivo());
        }
    }

    /** @param existencia {@code null}: no controla existencias, o el local no tiene almacén */
    public record RespuestaDisponible(UUID id, String codigo, String nombre, String unidad,
            String unidadNombre, String afectacion, boolean llevaIgv, BigDecimal precio,
            boolean controlaStock, BigDecimal existencia) {

        static RespuestaDisponible desde(Productos.ProductoDisponible d) {
            var p = d.producto();
            return new RespuestaDisponible(p.id(), p.codigo(), p.nombre(), p.unidad().codigo(),
                    p.unidad().nombre(), p.afectacion().name(), p.afectacion().llevaIgv(), d.precio(),
                    p.controlaStock(), d.existencia());
        }
    }

    public record RespuestaDisponibilidad(UUID sucursalId, boolean disponible, BigDecimal precio) {

        static RespuestaDisponibilidad desde(DisponibilidadEnLocal d) {
            return new RespuestaDisponibilidad(d.sucursalId(), d.estaDisponible(), d.precio());
        }
    }

    public record RespuestaExistencia(UUID almacenId, BigDecimal cantidad) {

        static RespuestaExistencia desde(Existencia e) {
            return new RespuestaExistencia(e.almacenId(), e.cantidad());
        }
    }

    public record RespuestaMovimiento(UUID id, UUID almacenId, BigDecimal cantidad, String tipo,
            String documentoTipo, UUID documentoId, String motivo, UUID usuarioId, Instant creadoEn) {

        static RespuestaMovimiento desde(MovimientoStock m) {
            return new RespuestaMovimiento(m.id(), m.almacenId(), m.cantidad(), m.tipo().name(),
                    m.documentoTipo(), m.documentoId(), m.motivo(), m.usuarioId(), m.creadoEn());
        }
    }
}
