package com.ondexia.consultas;

import com.ondexia.domain.consultas.ConsultaDeDni;
import com.ondexia.domain.consultas.ConsultaNoDisponible;
import com.ondexia.domain.consultas.DatosDeDni;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** La misma cascada que {@link CascadaDeProveedores}, para RENIEC. */
class CascadaDeConsultasDeDni implements ConsultaDeDni {

    private static final Logger LOG = LoggerFactory.getLogger(CascadaDeConsultasDeDni.class);

    private final List<ConsultaDeDni> proveedores;

    CascadaDeConsultasDeDni(List<ConsultaDeDni> proveedores) {
        if (proveedores.isEmpty()) {
            throw new IllegalArgumentException("Una cascada sin proveedores no consulta nada.");
        }
        this.proveedores = List.copyOf(proveedores);
    }

    @Override
    public Optional<DatosDeDni> consultar(String dni) {
        ConsultaNoDisponible ultimoFallo = null;
        boolean algunoReintentable = false;
        for (ConsultaDeDni proveedor : proveedores) {
            try {
                return proveedor.consultar(dni);
            } catch (ConsultaNoDisponible fallo) {
                ultimoFallo = fallo;
                algunoReintentable = algunoReintentable || fallo.esReintentable();
                LOG.warn("{} no pudo resolver el DNI; se pasa al siguiente proveedor",
                        proveedor.getClass().getSimpleName());
            }
        }
        throw new ConsultaNoDisponible(
                "consulta_no_disponible",
                "No pudimos consultar el DNI en este momento.",
                algunoReintentable,
                ultimoFallo);
    }
}
