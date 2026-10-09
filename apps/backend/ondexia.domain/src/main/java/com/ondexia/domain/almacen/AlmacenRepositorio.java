package com.ondexia.domain.almacen;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida de almacenes.
 *
 * <p>Ninguna operación recibe la empresa. Esta es la primera tabla protegida por
 * Row Level Security: la política filtra por {@code empresa_id} contra la
 * variable de sesión que fija {@code GestorTransaccionesConAislamiento}, así que
 * el adaptador no puede ver filas de otra empresa aunque se lo pidieran.
 *
 * <p>Es una diferencia real con {@code SucursalRepositorio}, que sí recibe el
 * identificador porque su tabla queda fuera de RLS —hay que leerla <em>para
 * saber</em> cuál es la empresa—. Aquí no hace falta, y por tanto no se ofrece:
 * lo que no se puede pedir no se puede pedir mal.
 */
public interface AlmacenRepositorio {

    Optional<Almacen> buscarPorId(UUID id);

    Optional<Almacen> buscarPorCodigo(String codigo);

    /** Todos los de la empresa activa, activos e inactivos. */
    List<Almacen> listar();

    /**
     * El almacén con el que opera un establecimiento: el activo <strong>más
     * antiguo</strong> de los que cuelgan de él, que es el que nació con el
     * establecimiento ({@code DotacionDeEstablecimiento}).
     *
     * <p>Hubo un «el primero por código». Con un solo almacén por local da lo
     * mismo; con dos, el que recibía la descarga del mostrador cambiaba según
     * cómo se llamara el nuevo, y lo descubrió el CI: una prueba creaba
     * {@code ALM-STK} en la matriz antes que la del punto de venta y el
     * cemento salía de un almacén vacío. Antigüedad es un criterio que no
     * depende de un nombre y que el usuario puede predecir: el almacén de
     * siempre sigue siendo el del mostrador hasta que exista una forma de
     * elegirlo.
     */
    Optional<Almacen> principalDe(UUID sucursalId);

    Almacen guardar(Almacen almacen);
}
