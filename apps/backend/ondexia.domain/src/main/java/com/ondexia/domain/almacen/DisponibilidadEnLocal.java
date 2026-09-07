package com.ondexia.domain.almacen;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Si un producto se vende en un establecimiento, y a qué precio (doc 12 §3.5).
 *
 * <p>La ausencia de fila significa «no disponible»: un producto nuevo se ofrece
 * en el local donde se creó y en ningún otro hasta que alguien lo diga. El
 * precio a null es «el de lista»; con valor, el propio del local.
 */
public class DisponibilidadEnLocal {

    private final UUID id;
    private final UUID productoId;
    private final UUID sucursalId;
    private boolean disponible;
    private BigDecimal precio;

    public DisponibilidadEnLocal(UUID id, UUID productoId, UUID sucursalId, boolean disponible,
            BigDecimal precio) {
        this.id = Objects.requireNonNull(id, "id");
        this.productoId = Objects.requireNonNull(productoId, "productoId");
        this.sucursalId = Objects.requireNonNull(sucursalId, "sucursalId");
        fijar(disponible, precio);
    }

    public void fijar(boolean disponible, BigDecimal precio) {
        this.disponible = disponible;
        this.precio = precio == null ? null : Producto.exigirPrecio(precio, "precio_invalido",
                "El precio del local no puede ser negativo ni tener más de seis decimales.");
    }

    public BigDecimal precioEfectivo(BigDecimal precioLista) {
        return precio == null ? precioLista : precio;
    }

    public UUID id() {
        return id;
    }

    public UUID productoId() {
        return productoId;
    }

    public UUID sucursalId() {
        return sucursalId;
    }

    public boolean estaDisponible() {
        return disponible;
    }

    /** Null cuando rige el precio de lista. */
    public BigDecimal precio() {
        return precio;
    }
}
