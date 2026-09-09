package com.ondexia.domain.ventas;

import com.ondexia.domain.almacen.AfectacionIgv;
import com.ondexia.domain.almacen.UnidadDeMedida;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Una línea del documento, ya calculada.
 *
 * <p>La descripción, la unidad y la afectación se copian del producto: el
 * producto puede cambiar de nombre y el comprobante no.
 *
 * <h2>La aritmética parte del total, no del valor sin IGV</h2>
 *
 * <p>El precio unitario lleva el IGV incluido: es lo que ve el cliente en el
 * mostrador, y lo que paga tiene que ser exactamente cantidad por precio menos
 * el descuento. Si se calculara al revés —valor sin IGV redondeado, más el 18 %
 * redondeado— dos bolsas a S/ 32.50 costarían S/ 64.99, y un cliente que paga
 * S/ 65.00 no acepta que le devuelvan un céntimo que no existe. Por eso el
 * total de la línea se fija primero, el valor de venta es el total entre 1.18 y
 * el IGV es la diferencia. SUNAT valida el IGV de la línea con tolerancia de un
 * céntimo, y ambos valores caen dentro. El valor unitario sin IGV se guarda a
 * seis decimales para el XML.
 *
 * @param descuento sobre el total de la línea, con IGV, en soles: como el precio.
 *                  Cero si no hay
 */
public record LineaDeVenta(
        int orden,
        UUID productoId,
        String codigo,
        String descripcion,
        UnidadDeMedida unidad,
        BigDecimal cantidad,
        BigDecimal precioUnitario,
        BigDecimal valorUnitario,
        BigDecimal descuento,
        AfectacionIgv afectacion,
        BigDecimal valorVenta,
        BigDecimal igv,
        BigDecimal total,
        boolean descargaExistencias) {

    /** Construye la línea desde lo que se vende y a qué precio; calcula el resto. */
    public static LineaDeVenta calcular(int orden, UUID productoId, String codigo,
            String descripcion, UnidadDeMedida unidad, AfectacionIgv afectacion,
            BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal descuento,
            boolean descargaExistencias) {
        Objects.requireNonNull(productoId, "productoId");
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new ReglaDeNegocioViolada(
                    "cantidad_invalida", "La cantidad de cada línea tiene que ser mayor que cero.",
                    "lineas");
        }
        if (precioUnitario == null || precioUnitario.signum() < 0) {
            throw new ReglaDeNegocioViolada(
                    "precio_invalido", "El precio no puede ser negativo.", "lineas");
        }
        BigDecimal rebaja = descuento == null ? BigDecimal.ZERO : descuento;
        if (rebaja.signum() < 0) {
            throw new ReglaDeNegocioViolada(
                    "descuento_invalido", "El descuento no puede ser negativo.", "lineas");
        }

        BigDecimal total = cantidad.multiply(precioUnitario).subtract(rebaja)
                .setScale(2, RoundingMode.HALF_UP);
        if (total.signum() < 0) {
            throw new ReglaDeNegocioViolada(
                    "descuento_invalido", "El descuento no puede superar el total de la línea.",
                    "lineas");
        }
        BigDecimal valorUnitario = afectacion.llevaIgv()
                ? precioUnitario.divide(DocumentoVenta.FACTOR_IGV, 6, RoundingMode.HALF_UP)
                : precioUnitario.setScale(6, RoundingMode.HALF_UP);
        BigDecimal valorVenta = afectacion.llevaIgv()
                ? total.divide(DocumentoVenta.FACTOR_IGV, 2, RoundingMode.HALF_UP)
                : total;
        BigDecimal igv = total.subtract(valorVenta);

        return new LineaDeVenta(orden, productoId, codigo, descripcion, unidad,
                cantidad, precioUnitario.setScale(6, RoundingMode.HALF_UP), valorUnitario,
                rebaja.setScale(2, RoundingMode.HALF_UP), afectacion, valorVenta, igv, total,
                descargaExistencias);
    }
}
