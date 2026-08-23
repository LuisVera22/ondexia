package com.ondexia.infrastructure.salida.consultas;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondexia.infrastructure.configuration.PropiedadesConsultas;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Qué se monta según las variables de entorno que haya.
 *
 * <h2>Por qué esto se prueba</h2>
 *
 * <p>Porque es el interruptor de toda la función y no se ve. Con la variable mal
 * escrita, la aplicación arranca igual, no hay error en ninguna parte, y la
 * verificación de RUC simplemente no existe. Un entorno así es
 * indistinguible de uno bien configurado hasta que alguien intenta registrar una
 * empresa.
 */
class ConsultasConfigTest {

    private static PropiedadesConsultas con(String decolecta, String apiperu) {
        return new PropiedadesConsultas(null, decolecta, null, apiperu, Duration.ofSeconds(6));
    }

    @Test
    @DisplayName("sin ninguna clave, se monta el que falla diciéndolo")
    void sin_claves_no_hay_consulta() {
        assertThat(new ConsultasConfig().consultaDeRuc(con(null, null)))
                .isInstanceOf(ConsultaDeRucSinConfigurar.class);
    }

    /**
     * La trampa concreta: en Docker y en IntelliJ, una variable declarada y sin
     * valor llega como cadena vacía, no como nulo. Sin normalizarla, el
     * proveedor entraría en la cascada y llamaría con un Bearer vacío — un 401
     * en cada registro, con el aspecto de un problema de nuestro proveedor.
     */
    @Test
    @DisplayName("una variable declarada y vacía no cuenta como configurada")
    void la_cadena_vacia_no_es_una_clave() {
        assertThat(new ConsultasConfig().consultaDeRuc(con("   ", "")))
                .isInstanceOf(ConsultaDeRucSinConfigurar.class);
    }

    @Test
    @DisplayName("con una sola clave, la cascada se monta igual")
    void una_clave_basta() {
        assertThat(new ConsultasConfig().consultaDeRuc(con("clave-de-prueba", null)))
                .isInstanceOf(ConsultaDeRucEnCascada.class);
        assertThat(new ConsultasConfig().consultaDeRuc(con(null, "clave-de-prueba")))
                .isInstanceOf(ConsultaDeRucEnCascada.class);
    }

    @Test
    void con_las_dos_tambien() {
        assertThat(new ConsultasConfig().consultaDeRuc(con("una", "otra")))
                .isInstanceOf(ConsultaDeRucEnCascada.class);
    }

    /** Los valores por omisión existen para no repetir la URL en cada entorno. */
    @Test
    void las_direcciones_tienen_defecto_y_las_claves_no() {
        PropiedadesConsultas propiedades = new PropiedadesConsultas(null, null, null, null, null);

        assertThat(propiedades.decolectaUrl()).isEqualTo("https://api.decolecta.com/v1");
        assertThat(propiedades.apiperuUrl()).isEqualTo("https://api.apiperu.dev");
        assertThat(propiedades.decolectaToken())
                .withFailMessage("una clave por omisión produciría un 401 en vez de «sin configurar»")
                .isNull();
        assertThat(propiedades.tiempoDeEspera())
                .withFailMessage("sin tiempo de espera, un proveedor lento acaba en un 504 de la pasarela")
                .isEqualTo(Duration.ofSeconds(6));
    }
}
