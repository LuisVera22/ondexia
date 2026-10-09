package com.ondexia.domain.ventas;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo vendido en una fecha, para el panel.
 *
 * <p>Es un puerto de lectura y no un método del repositorio de documentos por
 * lo mismo que {@link CobrosDeSesion}: sumar no es reconstruir. Traer los
 * documentos del día para sumarlos en memoria funciona con veinte y deja de
 * funcionar con dos mil, y ninguna de las dos cifras necesita el agregado.
 */
public interface VentasDelDia {

    /**
     * @param documentos cuántos se emitieron, sin contar los anulados
     * @param importe suma de los totales, con las notas de crédito restando
     */
    record Totales(int documentos, BigDecimal importe) {

        public static Totales vacio() {
            return new Totales(0, BigDecimal.ZERO);
        }
    }

    /**
     * Lo vendido en {@code fecha}, en la zona horaria del negocio.
     *
     * <p>Cuenta la nota de venta junto a la boleta y la factura: en el mostrador
     * las tres son una venta y el dinero entró igual. Lo que las separa —que una
     * no se declara— importa para SUNAT, no para saber cuánto se vendió hoy.
     */
    Totales de(LocalDate fecha);
}
