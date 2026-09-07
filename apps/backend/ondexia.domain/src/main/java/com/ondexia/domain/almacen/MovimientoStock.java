package com.ondexia.domain.almacen;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Una entrada o salida de existencias: el libro mayor del que {@link Existencia}
 * es la proyección. Solo se inserta, nunca se corrige: un error se compensa con
 * otro movimiento.
 *
 * @param cantidad con signo: positiva entra, negativa sale. Nunca cero
 * @param documentoTipo y documentoId, lo que originó el movimiento cuando fue
 *        un documento (una venta, una devolución). Un ajuste no tiene
 */
public record MovimientoStock(
        UUID id,
        UUID almacenId,
        UUID productoId,
        BigDecimal cantidad,
        Tipo tipo,
        String documentoTipo,
        UUID documentoId,
        String motivo,
        UUID usuarioId,
        Instant creadoEn) {

    public enum Tipo { AJUSTE, INGRESO, VENTA, DEVOLUCION, TRASLADO }

    public MovimientoStock {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(almacenId, "almacenId");
        Objects.requireNonNull(productoId, "productoId");
        Objects.requireNonNull(tipo, "tipo");
        if (cantidad == null || cantidad.signum() == 0) {
            throw new ReglaDeNegocioViolada(
                    "movimiento_sin_cantidad", "Un movimiento de existencias mueve algo: la cantidad no puede ser cero.");
        }
    }

    public static MovimientoStock ajuste(UUID almacenId, UUID productoId, BigDecimal diferencia,
            String motivo, UUID usuarioId, Instant ahora) {
        return new MovimientoStock(UUID.randomUUID(), almacenId, productoId, diferencia,
                Tipo.AJUSTE, null, null, motivo == null || motivo.isBlank() ? null : motivo.trim(),
                usuarioId, ahora);
    }
}
