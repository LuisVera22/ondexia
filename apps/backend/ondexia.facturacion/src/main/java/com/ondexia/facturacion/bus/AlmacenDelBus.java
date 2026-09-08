package com.ondexia.facturacion.bus;

import java.util.Optional;

/**
 * El bucket del bus visto desde el Emisor (doc 14 §2): leer una orden o una
 * credencial, dejar un XML, un CDR o un resultado. En AWS es S3; en local, un
 * directorio.
 */
public interface AlmacenDelBus {

    /** {@code empty} si el objeto no existe. */
    Optional<byte[]> leer(String clave);

    void escribir(String clave, byte[] contenido, String tipoContenido);

    /** Borrar una orden ya procesada. Si no existe, no pasa nada. */
    void borrar(String clave);
}
