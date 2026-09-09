package com.ondexia.application.almacen;

import com.ondexia.domain.almacen.AfectacionIgv;
import com.ondexia.domain.almacen.AlmacenRepositorio;
import com.ondexia.domain.almacen.Existencia;
import com.ondexia.domain.almacen.ExistenciasRepositorio;
import com.ondexia.domain.almacen.DisponibilidadEnLocal;
import com.ondexia.domain.almacen.DisponibilidadRepositorio;
import com.ondexia.domain.almacen.Producto;
import com.ondexia.domain.almacen.ProductoRepositorio;
import com.ondexia.domain.almacen.UnidadDeMedida;
import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.SucursalRepositorio;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El catálogo de la empresa activa y su disponibilidad por local (doc 12 §3.5).
 *
 * <p>El catálogo es de la empresa: la unidad y la afectación al IGV son
 * atributos del bien y no pueden discrepar entre dos comprobantes del mismo
 * RUC. Lo que es del local —se vende o no, a qué precio— va aparte, y un
 * producto nuevo solo se ofrece en el local donde se creó.
 */
@Service
public class Productos {

    private static final int MAXIMO_BUSQUEDA = 50;

    private final ProductoRepositorio productos;
    private final DisponibilidadRepositorio disponibilidad;
    private final SucursalRepositorio sucursales;
    private final AlmacenRepositorio almacenes;
    private final ExistenciasRepositorio existencias;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;

    public Productos(ProductoRepositorio productos, DisponibilidadRepositorio disponibilidad,
            SucursalRepositorio sucursales, AlmacenRepositorio almacenes,
            ExistenciasRepositorio existencias, RegistroDeAuditoria auditoria,
            ProveedorDeContexto contexto) {
        this.productos = productos;
        this.disponibilidad = disponibilidad;
        this.sucursales = sucursales;
        this.almacenes = almacenes;
        this.existencias = existencias;
        this.auditoria = auditoria;
        this.contexto = contexto;
    }

    /**
     * Lo que el punto de venta necesita de un producto en un local: el producto,
     * el precio que rige ahí y cuánto hay en el almacén del local.
     *
     * @param existencia {@code null} si el producto no controla existencias o el
     *                   local no tiene almacén
     */
    public record ProductoDisponible(Producto producto, BigDecimal precio, BigDecimal existencia) {
    }

    /** Un producto activo y disponible en el establecimiento, con su precio efectivo. */
    public java.util.Optional<ProductoDisponible> disponibleEn(UUID productoId, UUID sucursalId) {
        return productos.buscarPorId(productoId)
                .filter(Producto::estaActivo)
                .flatMap(producto -> disponibilidad.buscar(productoId, sucursalId)
                        .filter(DisponibilidadEnLocal::estaDisponible)
                        .map(local -> new ProductoDisponible(producto,
                                local.precioEfectivo(producto.precioLista()),
                                existenciaEn(producto, sucursalId))));
    }

    /**
     * El catálogo del local para el mostrador: activos, disponibles ahí, por
     * código o nombre. Hasta {@value #MAXIMO_BUSQUEDA}: es un autocompletado.
     */
    public List<ProductoDisponible> disponiblesEn(UUID sucursalId, String texto) {
        var enElLocal = disponibilidad.listarDisponiblesEn(sucursalId).stream()
                .collect(java.util.stream.Collectors.toMap(DisponibilidadEnLocal::productoId, d -> d));
        return buscar(texto).stream()
                .filter(Producto::estaActivo)
                .filter(p -> enElLocal.containsKey(p.id()))
                .limit(MAXIMO_BUSQUEDA)
                .map(p -> new ProductoDisponible(p,
                        enElLocal.get(p.id()).precioEfectivo(p.precioLista()),
                        existenciaEn(p, sucursalId)))
                .toList();
    }

    private BigDecimal existenciaEn(Producto producto, UUID sucursalId) {
        if (!producto.controlaStock()) {
            return null;
        }
        return almacenes.principalDe(sucursalId)
                .map(almacen -> existencias.buscar(almacen.id(), producto.id())
                        .map(Existencia::cantidad).orElse(BigDecimal.ZERO))
                .orElse(null);
    }

    public record Datos(String nombre, String descripcion, UnidadDeMedida unidad,
            AfectacionIgv afectacion, BigDecimal precioLista, boolean controlaStock) {
    }

    public List<Producto> listar() {
        return productos.listar();
    }

    public List<Producto> buscar(String texto) {
        if (texto == null || texto.isBlank()) {
            return productos.listar();
        }
        return productos.buscar(texto.trim(), MAXIMO_BUSQUEDA);
    }

    public Producto obtener(UUID id) {
        return exigir(id);
    }

    /**
     * @param sucursalId el local donde nace disponible. Obligatorio: un producto
     *                   que no se vende en ningún sitio no es un producto, es un
     *                   borrador, y el punto de venta no lo mostraría en ninguna
     *                   pantalla sin que nadie sepa por qué
     */
    @Transactional
    public Producto registrar(String codigo, Datos datos, UUID sucursalId) {
        var producto = new Producto(UUID.randomUUID(), empresaActiva(), codigo, datos.nombre(),
                datos.descripcion(), datos.unidad(), datos.afectacion(), datos.precioLista(),
                datos.controlaStock());

        // Cortesía: el índice único da la garantía, esto da el mensaje.
        productos.buscarPorCodigo(producto.codigo()).ifPresent(existente -> {
            throw new Conflicto(
                    "codigo_duplicado",
                    "Ya existe un producto con el código " + producto.codigo() + ".",
                    "codigo");
        });

        var guardado = productos.guardar(producto);
        auditoria.registrarCreacion("producto", guardado.id(), Instantanea.de(guardado));

        disponibilidad.guardar(new DisponibilidadEnLocal(UUID.randomUUID(), guardado.id(),
                validarSucursal(sucursalId), true, null));
        return guardado;
    }

    @Transactional
    public Producto actualizar(UUID id, Datos datos) {
        var producto = exigir(id);
        var antes = Instantanea.de(producto);
        producto.actualizar(datos.nombre(), datos.descripcion(), datos.unidad(),
                datos.afectacion(), datos.precioLista(), datos.controlaStock());
        var guardado = productos.guardar(producto);
        auditoria.registrarActualizacion("producto", id, antes, Instantanea.de(guardado));
        return guardado;
    }

    @Transactional
    public Producto cambiarEstado(UUID id, boolean activo) {
        var producto = exigir(id);
        if (producto.estaActivo() == activo) {
            return producto; // Idempotente.
        }
        var antes = Instantanea.de(producto);
        if (activo) {
            producto.activar();
        } else {
            producto.desactivar();
        }
        var guardado = productos.guardar(producto);
        auditoria.registrar("producto", id, activo ? "ACTIVAR" : "DESACTIVAR",
                antes, Instantanea.de(guardado));
        return guardado;
    }

    // ── Disponibilidad por local ────────────────────────────────────────────

    /** Las filas que existen. Un local sin fila no ofrece el producto. */
    public List<DisponibilidadEnLocal> disponibilidadDe(UUID productoId) {
        exigir(productoId);
        return disponibilidad.listarDeProducto(productoId);
    }

    @Transactional
    public DisponibilidadEnLocal fijarDisponibilidad(UUID productoId, UUID sucursalId,
            boolean disponible, BigDecimal precio) {
        exigir(productoId);
        var sucursal = validarSucursal(sucursalId);
        var actual = disponibilidad.buscar(productoId, sucursal)
                .orElseGet(() -> new DisponibilidadEnLocal(UUID.randomUUID(), productoId, sucursal,
                        disponible, precio));
        var antes = new InstantaneaLocal(actual.sucursalId(), actual.estaDisponible(), actual.precio());
        actual.fijar(disponible, precio);
        var guardada = disponibilidad.guardar(actual);
        auditoria.registrarActualizacion("producto_local", productoId, antes,
                new InstantaneaLocal(guardada.sucursalId(), guardada.estaDisponible(), guardada.precio()));
        return guardada;
    }

    Producto exigir(UUID id) {
        return productos.buscarPorId(id)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "producto_no_encontrado", "El producto no existe."));
    }

    private UUID validarSucursal(UUID sucursalId) {
        if (sucursalId == null) {
            throw new ReglaDeNegocioViolada(
                    "establecimiento_requerido",
                    "Indica el establecimiento en el que se ofrece el producto.", "sucursalId");
        }
        return sucursales.buscarPorId(sucursalId)
                .filter(s -> s.empresaId().equals(empresaActiva()))
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "establecimiento_invalido",
                        "El establecimiento indicado no existe en esta empresa.", "sucursalId"))
                .id();
    }

    private UUID empresaActiva() {
        return contexto.obligatorio().empresaActivaObligatoria();
    }

    private record Instantanea(String codigo, String nombre, String unidad, String afectacion,
            BigDecimal precioLista, boolean controlaStock, boolean activo) {

        static Instantanea de(Producto p) {
            return new Instantanea(p.codigo(), p.nombre(), p.unidad().codigo(),
                    p.afectacion().codigo(), p.precioLista(), p.controlaStock(), p.estaActivo());
        }
    }

    private record InstantaneaLocal(UUID sucursalId, boolean disponible, BigDecimal precio) {
    }
}
