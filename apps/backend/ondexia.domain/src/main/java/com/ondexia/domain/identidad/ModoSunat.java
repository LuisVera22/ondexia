package com.ondexia.domain.identidad;

/**
 * Entorno de SUNAT contra el que emite una empresa.
 *
 * <p>Es un campo <strong>por empresa</strong> y no una configuracion de la
 * aplicacion. Un mismo despliegue puede tener un cliente ya homologado
 * emitiendo en produccion y otro todavia probando contra la beta, y el error de
 * mandar a produccion lo que iba a beta no es recuperable: el comprobante queda
 * emitido de verdad.
 */
public enum ModoSunat {
    BETA,
    PRODUCCION
}
