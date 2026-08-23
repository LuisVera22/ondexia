package com.ondexia.infrastructure.salida.consultas;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.consultas.ConsultaDeRuc;
import com.ondexia.domain.consultas.ConsultaNoDisponible;
import com.ondexia.domain.consultas.DatosDeRuc;
import java.util.Optional;

/**
 * Lo que se usa cuando no hay ninguna clave de proveedor.
 *
 * <h2>Por qué existe en vez de no registrar el bean</h2>
 *
 * <p>Sin bean, la aplicación no arranca en el único entorno donde no hace falta
 * ninguna clave: la Lambda de la API, que no tiene salida a internet y cuya
 * consulta la hace {@code ondexia.consultas} (DT-19).
 *
 * <h2>Por qué falla en vez de devolver vacío</h2>
 *
 * <p>Porque vacío significa «el padrón no conoce ese RUC». Un doble silencioso
 * que devolviera vacío le diría a todo el mundo que su RUC no existe, y el
 * mensaje culparía a quien no tiene la culpa: la persona corregiría un número
 * correcto una y otra vez mientras el problema es que a nosotros nos falta una
 * variable de entorno.
 *
 * <p>No reintentable: no hay nada que reintentar hasta que alguien configure una
 * clave.
 */
class ConsultaDeRucSinConfigurar implements ConsultaDeRuc {

    @Override
    public Optional<DatosDeRuc> consultar(Ruc ruc) {
        throw new ConsultaNoDisponible(
                "consulta_sin_configurar",
                "La verificación de RUC contra SUNAT no está configurada en este entorno.",
                false);
    }
}
