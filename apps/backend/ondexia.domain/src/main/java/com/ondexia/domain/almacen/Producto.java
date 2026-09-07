package com.ondexia.domain.almacen;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Un bien o servicio del catálogo de la empresa (doc 12 §3.5).
 *
 * <p>Lo que vive aquí es lo que define al bien ante SUNAT y lo que comparten
 * todos los locales: código, nombre, unidad, afectación y precio de lista. Si
 * se vende en un local, a qué precio y cuánto hay es de
 * {@link DisponibilidadEnLocal} y de {@link Existencia}.
 *
 * <p>{@code controlaStock} distingue el bien del servicio: un servicio no tiene
 * existencias y venderlo no descarga nada.
 */
public class Producto {

    private static final int DECIMALES_MAXIMOS = 6;

    private final UUID id;
    private final UUID empresaId;
    private final String codigo;
    private String nombre;
    private String descripcion;
    private UnidadDeMedida unidad;
    private AfectacionIgv afectacion;
    private BigDecimal precioLista;
    private boolean controlaStock;
    private boolean activo;

    public Producto(UUID id, UUID empresaId, String codigo, String nombre, String descripcion,
            UnidadDeMedida unidad, AfectacionIgv afectacion, BigDecimal precioLista,
            boolean controlaStock) {
        this.id = Objects.requireNonNull(id, "id");
        this.empresaId = Objects.requireNonNull(empresaId, "empresaId");
        this.codigo = normalizarCodigo(codigo);
        this.activo = true;
        actualizar(nombre, descripcion, unidad, afectacion, precioLista, controlaStock);
    }

    /** Reconstrucción desde la persistencia: no valida. */
    public Producto(UUID id, UUID empresaId, String codigo, String nombre, String descripcion,
            UnidadDeMedida unidad, AfectacionIgv afectacion, BigDecimal precioLista,
            boolean controlaStock, boolean activo) {
        this.id = id;
        this.empresaId = empresaId;
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.unidad = unidad;
        this.afectacion = afectacion;
        this.precioLista = precioLista;
        this.controlaStock = controlaStock;
        this.activo = activo;
    }

    public void actualizar(String nombre, String descripcion, UnidadDeMedida unidad,
            AfectacionIgv afectacion, BigDecimal precioLista, boolean controlaStock) {
        this.nombre = exigirNombre(nombre);
        this.descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
        this.unidad = Objects.requireNonNull(unidad, "unidad");
        this.afectacion = Objects.requireNonNull(afectacion, "afectacion");
        this.precioLista = exigirPrecio(precioLista, "precio_lista_invalido",
                "El precio de lista no puede ser negativo ni tener más de seis decimales.");
        this.controlaStock = controlaStock;
    }

    public void desactivar() {
        this.activo = false;
    }

    public void activar() {
        this.activo = true;
    }

    public UUID id() {
        return id;
    }

    public UUID empresaId() {
        return empresaId;
    }

    public String codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    public String descripcion() {
        return descripcion;
    }

    public UnidadDeMedida unidad() {
        return unidad;
    }

    public AfectacionIgv afectacion() {
        return afectacion;
    }

    public BigDecimal precioLista() {
        return precioLista;
    }

    public boolean controlaStock() {
        return controlaStock;
    }

    public boolean estaActivo() {
        return activo;
    }

    /** Un precio válido para cualquier importe del catálogo: cero o más, hasta seis decimales. */
    public static BigDecimal exigirPrecio(BigDecimal precio, String codigo, String mensaje) {
        BigDecimal valor = precio == null ? BigDecimal.ZERO : precio;
        if (valor.signum() < 0 || valor.stripTrailingZeros().scale() > DECIMALES_MAXIMOS) {
            throw new ReglaDeNegocioViolada(codigo, mensaje);
        }
        return valor;
    }

    private static String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaDeNegocioViolada("codigo_requerido", "El producto necesita un código.");
        }
        String limpio = codigo.trim().toUpperCase();
        if (!limpio.matches("[A-Z0-9-]{1,30}")) {
            throw new ReglaDeNegocioViolada(
                    "codigo_invalido",
                    "El código del producto admite letras, números y guiones, hasta 30 caracteres.");
        }
        return limpio;
    }

    private static String exigirNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaDeNegocioViolada("nombre_requerido", "El producto necesita un nombre.");
        }
        String limpio = nombre.trim();
        if (limpio.length() > 300) {
            throw new ReglaDeNegocioViolada(
                    "nombre_invalido", "El nombre del producto admite hasta 300 caracteres.");
        }
        return limpio;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Producto producto && id.equals(producto.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
