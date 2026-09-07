package com.ondexia.application.configuracion;

import com.ondexia.application.almacen.Almacenes;
import com.ondexia.application.ventas.Cajas;
import com.ondexia.domain.almacen.AlmacenRepositorio;
import com.ondexia.domain.identidad.Sucursal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que nace con cada establecimiento: un almacén y una caja.
 *
 * <p>Sin almacén no se pueden registrar existencias y sin caja no se puede
 * vender. Pedir que el cliente los cree a mano deja un sistema que «no vende»
 * el primer día sin decir por qué; es el mismo motivo por el que el alta crea
 * la casa matriz sola (doc 12 §3.4 y §3.5). El cliente puede renombrarlos o
 * añadir más; lo que no puede es quedarse sin ninguno sin darse cuenta.
 *
 * <p>Exige un contexto con la empresa del establecimiento activa: {@code almacen}
 * y {@code caja} están bajo Row Level Security y las inserciones se rechazan
 * fuera de ella. Quien crea la empresa en la misma petición lo consigue con
 * {@code OperarComoEmpresa}; quien añade un establecimiento ya opera dentro.
 */
@Service
public class DotacionDeEstablecimiento {

    /** El almacén de la casa matriz. Los demás llevan el código del local. */
    static final String CODIGO_ALMACEN_MATRIZ = "PRINCIPAL";
    static final String NOMBRE_ALMACEN_MATRIZ = "Almacén principal";
    static final String PREFIJO_ALMACEN = "ALM-";
    private static final String CODIGO_MATRIZ = "0000";

    private final Almacenes almacenes;
    private final AlmacenRepositorio catalogoDeAlmacenes;
    private final Cajas cajas;

    public DotacionDeEstablecimiento(Almacenes almacenes, AlmacenRepositorio catalogoDeAlmacenes,
            Cajas cajas) {
        this.almacenes = almacenes;
        this.catalogoDeAlmacenes = catalogoDeAlmacenes;
        this.cajas = cajas;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void dotar(Sucursal sucursal) {
        String codigo = codigoDeAlmacen(sucursal);
        // El código del almacén es único por empresa, y el cliente pudo haberlo
        // creado antes que el local al que se parece. En ese caso ya tiene su
        // almacén y crear otro fallaría el alta del establecimiento por un
        // «código duplicado» de un campo que no rellenó.
        if (catalogoDeAlmacenes.buscarPorCodigo(codigo).isEmpty()) {
            almacenes.registrar(codigo, nombreDeAlmacen(sucursal), sucursal.id());
        }
        // La caja es única por establecimiento y el establecimiento acaba de
        // nacer: no puede haber colisión.
        cajas.registrar(Cajas.CODIGO_PRIMERA, Cajas.NOMBRE_PRIMERA, sucursal.id());
    }

    private static String codigoDeAlmacen(Sucursal sucursal) {
        return CODIGO_MATRIZ.equals(sucursal.codigo())
                ? CODIGO_ALMACEN_MATRIZ
                : PREFIJO_ALMACEN + sucursal.codigo();
    }

    private static String nombreDeAlmacen(Sucursal sucursal) {
        return CODIGO_MATRIZ.equals(sucursal.codigo())
                ? NOMBRE_ALMACEN_MATRIZ
                : "Almacén " + sucursal.nombre();
    }
}
