package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.identidad.CuentaAdministrador;
import com.ondexia.domain.identidad.CuentaAdministradorRepositorio;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * El invariante que la matriz de permisos no puede expresar: una cuenta nunca se
 * queda sin administrador.
 *
 * <p>Quitar el último permiso de un rol es legítimo. Quitar el último
 * administrador deja la cuenta sin quien la gobierne y sin nadie capaz de
 * arreglarlo desde dentro — hace falta que intervengamos nosotros con acceso
 * directo a la base. Por eso es una restricción y no una regla de servicio.
 */
class AdministradorDeCuentaIT extends PruebaIntegracion {

    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private CuentaAdministradorRepositorio administradores;

    @Test
    @DisplayName("Borrar al único administrador falla y no deja la cuenta huérfana")
    void noSePuedeQuitarAlUltimoAdministrador() {
        var existentes = administradores.listarDeCuenta(CUENTA);
        assertThat(existentes)
                .as("los datos de ejemplo deben tener exactamente un administrador")
                .hasSize(1);

        assertThatThrownBy(() -> administradores.eliminar(existentes.get(0).id()))
                .isInstanceOf(Exception.class);

        assertThat(administradores.contarEnCuenta(CUENTA))
                .as("el borrado debe haberse deshecho entero")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("No se puede duplicar el mismo administrador")
    void noSeDuplicaElAdministrador() {
        long antes = administradores.contarEnCuenta(CUENTA);

        assertThatThrownBy(() -> administradores.guardar(
                new CuentaAdministrador(UUID.randomUUID(), CUENTA, UUID.fromString(USUARIO_DEMO))))
                .as("la restriccion de unicidad lo impide")
                .isInstanceOf(Exception.class);

        assertThat(administradores.contarEnCuenta(CUENTA)).isEqualTo(antes);
    }
}
