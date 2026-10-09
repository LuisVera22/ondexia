package com.ondexia.domain.ventas;

/**
 * Estado del documento de venta, de cara al negocio.
 *
 * <p>Es distinto del estado ante SUNAT, que vive en {@code comprobante_electronico}
 * (iteración 5) y solo existe para los fiscales. Aquí:
 *
 * <ul>
 *   <li>{@code EMITIDO}: una nota de venta, en cuanto se registra. No espera a nadie.</li>
 *   <li>{@code PENDIENTE}: una boleta o factura registrada y no enviada todavía.
 *       Pasará a {@code EMITIDO} cuando SUNAT la acepte.</li>
 *   <li>{@code CANJEADO}: la nota de venta que ya se convirtió en boleta o factura
 *       (iteración 6). Nunca se borra.</li>
 *   <li>{@code ANULADO}: por nota de crédito, comunicación de baja o resumen
 *       (iteración 6).</li>
 * </ul>
 */
public enum EstadoDocumento { EMITIDO, PENDIENTE, CANJEADO, ANULADO }
