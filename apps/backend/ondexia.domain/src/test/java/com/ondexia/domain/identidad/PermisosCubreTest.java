package com.ondexia.domain.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La regla de no elevación (doc 12 §6.3): cubrir es contener todos los códigos. */
class PermisosCubreTest {

    private static final Permisos VENDEDOR = new Permisos(Set.of(
            "ventas:acceder", "ventas.comprobante:acceder", "ventas.comprobante:emitir"));

    @Test
    @DisplayName("Un conjunto se cubre a sí mismo y a cualquier subconjunto")
    void cubreSubconjuntos() {
        assertThat(VENDEDOR.cubre(VENDEDOR)).isTrue();
        assertThat(VENDEDOR.cubre(new Permisos(Set.of("ventas:acceder")))).isTrue();
        assertThat(VENDEDOR.cubre(Permisos.ninguno())).isTrue();
        assertThat(VENDEDOR.cubre(null)).isTrue();
    }

    @Test
    @DisplayName("Un solo código de más basta para no cubrir")
    void noCubreLoQueNoTiene() {
        var conAnular = new Permisos(Set.of("ventas:acceder", "ventas.comprobante:anular"));

        assertThat(VENDEDOR.cubre(conAnular)).isFalse();
        assertThat(Permisos.ninguno().cubre(VENDEDOR)).isFalse();
    }
}
