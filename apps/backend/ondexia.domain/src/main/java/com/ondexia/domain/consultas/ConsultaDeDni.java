package com.ondexia.domain.consultas;

import java.util.Optional;

/** Puerto hacia RENIEC a través de un proveedor. Vacío si el DNI no existe. */
public interface ConsultaDeDni {

    Optional<DatosDeDni> consultar(String dni);
}
