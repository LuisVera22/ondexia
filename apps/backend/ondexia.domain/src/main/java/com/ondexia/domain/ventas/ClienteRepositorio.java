package com.ondexia.domain.ventas;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteRepositorio {

    Optional<Cliente> buscarPorId(UUID id);

    Optional<Cliente> buscarPorDocumento(TipoDocumentoIdentidad tipo, String numero);

    /** Por nombre o por número de documento. */
    List<Cliente> buscar(String texto, int maximo);

    List<Cliente> listar();

    Cliente guardar(Cliente cliente);
}
