package com.ondexia.domain.identidad;

/**
 * Los tres escalones del catálogo de permisos.
 *
 * <p>La jerarquía es <strong>conjuntiva</strong>: para poder consultar productos
 * hacen falta los tres —{@code almacen}, {@code almacen.producto} y
 * {@code almacen.producto:consultar}—. Apagar el módulo deja fuera todo lo que
 * cuelga de él aunque sus casillas sigan marcadas.
 *
 * <p>Es lo que permite retirar un área entera con un interruptor sin repasar
 * treinta casillas, y lo que da un sitio evidente donde colgar cada módulo nuevo.
 */
public enum NivelPermiso {

    /** {@code almacen}. Sin punto en el código. */
    MODULO,

    /** {@code almacen.producto}. */
    SUBMODULO,

    /** {@code almacen.producto:consultar}. La capacidad concreta. */
    FUNCION
}
