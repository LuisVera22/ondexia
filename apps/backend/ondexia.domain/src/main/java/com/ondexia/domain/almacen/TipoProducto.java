package com.ondexia.domain.almacen;

/** Tipo explícito del catálogo; la unidad fiscal no sustituye esta clasificación. */
public enum TipoProducto {
    BIEN, SERVICIO;

    public boolean admite(UnidadDeMedida unidad) {
        return this == SERVICIO
                ? unidad == UnidadDeMedida.ZZ || unidad == UnidadDeMedida.HUR || unidad == UnidadDeMedida.DAY
                : unidad != UnidadDeMedida.ZZ;
    }
}
