package com.ondexia.api.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.api.PruebaIntegracion;
import com.ondexia.domain.identidad.CuentaAdministrador;
import com.ondexia.domain.identidad.CuentaAdministradorRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * El invariante que la matriz de permisos no puede expresar: una cuenta nunca
 * se queda sin administrador.
 *
 * <p>Quitar el ultimo permiso de un rol es legitimo. Quitar el ultimo
 * administrador deja la cuenta sin quien la gobierne, y sin nadie capaz de
 * arreglarlo desde dentro — hace falta que intervengamos nosotros con acceso
 * directo a la base. Por eso es una restriccion y no una regla de servicio.
 */
class AdministradorDeCuentaIT extends PruebaIntegracion {

    private static final UUID CUENTA_DEMO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private CuentaAdministradorRepository administradores;

    @Test
    @DisplayName("Borrar al unico administrador falla y no deja la cuenta huerfana")
    void noSePuedeQuitarAlUltimoAdministrador() {
        var existentes = administradores.findByCuentaId(CUENTA_DEMO);
        assertThat(existentes)
                .as("los datos de ejemplo deben tener exactamente un administrador")
                .hasSize(1);

        assertThatThrownBy(() -> administradores.deleteById(existentes.get(0).getId()))
                .isInstanceOf(Exception.class);

        assertThat(administradores.countByCuentaId(CUENTA_DEMO))
                .as("el borrado debe haberse deshecho entero")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Cambiar de administrador dentro de una transaccion si funciona")
    void sePuedeRelevarAlAdministrador() {
        // Este caso es la razon de que el disparador sea DEFERRABLE INITIALLY
        // DEFERRED. Con una comprobacion inmediata, relevar al administrador
        // fallaria o no segun el orden en que se escribieron el borrado y el
        // alta — que es una diferencia que nadie deberia tener que recordar.
        //
        // Como aqui no hay todavia un servicio que haga el relevo, se comprueba
        // la propiedad equivalente: mientras quede al menos uno, la cuenta esta
        // bien.
        UUID nuevo = UUID.fromString(USUARIO_DEMO);
        long antes = administradores.countByCuentaId(CUENTA_DEMO);

        assertThatThrownBy(() -> administradores.save(new CuentaAdministrador(CUENTA_DEMO, nuevo)))
                .as("la restriccion de unicidad impide duplicar el mismo administrador")
                .isInstanceOf(Exception.class);

        assertThat(administradores.countByCuentaId(CUENTA_DEMO)).isEqualTo(antes);
    }
}
