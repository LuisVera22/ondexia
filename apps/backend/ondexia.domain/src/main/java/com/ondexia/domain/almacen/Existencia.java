package com.ondexia.domain.almacen;

import java.math.BigDecimal;
import java.util.UUID;

/** Cuánto hay de un producto en un almacén: la proyección de {@link MovimientoStock}. */
public record Existencia(UUID almacenId, UUID productoId, BigDecimal cantidad) {
}
