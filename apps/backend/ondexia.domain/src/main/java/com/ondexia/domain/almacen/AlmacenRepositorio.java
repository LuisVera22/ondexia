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

    Almacen guardar(Almacen almacen);
}
