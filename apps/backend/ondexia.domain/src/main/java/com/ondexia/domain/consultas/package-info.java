/**
 * Consultas de solo lectura a fuentes públicas externas.
 *
 * <h2>Por qué es un paquete y no parte de identidad</h2>
 *
 * Porque quien pregunta y quien responde no son el mismo dominio. Registrar una
 * empresa es asunto de {@code identidad}; saber qué dice SUNAT de un RUC no lo
 * es, y el día que haga falta el tipo de cambio no habría dónde ponerlo.
 *
 * <p>Aquí viven los <strong>puertos</strong> y su vocabulario. La
 * implementación no está en este repositorio de despliegue: vive en
 * {@code ondexia.consultas}, fuera de la VPC, porque la Lambda de la API no
 * tiene salida a internet (DTE DT-19 y §4.8).
 *
 * <h2>La valla</h2>
 *
 * Entra: solo lectura, dato público o semipúblico, sin secretos del cliente,
 * cacheable. No entra: nada que use el certificado o las credenciales SOL de un
 * cliente —eso es {@code facturacion}—, ni nada que mueva dinero o envíe
 * mensajes en su nombre.
 *
 * <p>Ver {@code ondexia.docs/11-registro-de-empresa.md}.
 */
package com.ondexia.domain.consultas;
