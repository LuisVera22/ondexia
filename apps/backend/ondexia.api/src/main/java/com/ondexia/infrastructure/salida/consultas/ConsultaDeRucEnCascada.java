package com.ondexia.infrastructure.salida.consultas;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.consultas.ConsultaDeRuc;
import com.ondexia.domain.consultas.ConsultaNoDisponible;
import com.ondexia.domain.consultas.DatosDeRuc;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pregunta por orden hasta que alguien responde.
 *
 * <h2>Un «no existe» detiene la cascada</h2>
 *
 * <p>Y es una decisión, no un descuido. Si el primer proveedor dice que el
 * padrón no conoce ese RUC, no se pregunta al siguiente: los tres leen la misma
 * fuente, así que el segundo diría lo mismo y el único efecto sería duplicar la
 * espera en el caso más frecuente de todos —una errata de tecleo.
 *
 * <p>Lo que sí pasa al siguiente es un <strong>fallo</strong>. Ahí la respuesta
 * no llegó, y el segundo proveedor puede tenerla.
 *
 * <h2>Por qué se sigue tras un fallo no reintentable</h2>
 *
 * <p>Porque «no reintentable» habla del mismo proveedor, no de la cascada. Una
 * clave de Decolecta caducada es exactamente la situación para la que existe el
 * relevo: reintentar con Decolecta es inútil, preguntar a apiperu.dev no.
 *
 * <h2>Lo que se propaga si nadie responde</h2>
 *
 * <p>El fallo se declara reintentable solo si <strong>alguno</strong> lo era. Si
 * todos respondieron que no y ninguno por algo pasajero, insistir no arregla
 * nada y quien está registrando su empresa merece saberlo en vez de esperar.
 */
class ConsultaDeRucEnCascada implements ConsultaDeRuc {

    private static final Logger LOG = LoggerFactory.getLogger(ConsultaDeRucEnCascada.class);

    private final List<ConsultaDeRuc> proveedores;

    ConsultaDeRucEnCascada(List<ConsultaDeRuc> proveedores) {
        if (proveedores.isEmpty()) {
            throw new IllegalArgumentException("Una cascada sin proveedores no consulta nada.");
        }
        this.proveedores = List.copyOf(proveedores);
    }

    @Override
    public Optional<DatosDeRuc> consultar(Ruc ruc) {
        ConsultaNoDisponible ultimoFallo = null;
        boolean algunoReintentable = false;

        for (ConsultaDeRuc proveedor : proveedores) {
            try {
                return proveedor.consultar(ruc);
            } catch (ConsultaNoDisponible fallo) {
                ultimoFallo = fallo;
                algunoReintentable = algunoReintentable || fallo.esReintentable();
                LOG.warn("{} no pudo resolver la consulta; se pasa al siguiente proveedor",
                        proveedor.getClass().getSimpleName());
            }
        }

        throw new ConsultaNoDisponible(
                "consulta_no_disponible",
                "No pudimos verificar el RUC contra SUNAT en este momento.",
                algunoReintentable,
                ultimoFallo);
    }
}
