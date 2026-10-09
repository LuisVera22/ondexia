package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.EmpresaRepositorio;
import com.ondexia.domain.identidad.Sucursal;
import com.ondexia.domain.identidad.SucursalRepositorio;
import com.ondexia.infrastructure.entrada.web.comun.TraductorRestricciones;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Que una restricción de la base llegue al cliente como un mensaje útil.
 *
 * <p>Antes de esto, registrar un RUC repetido producía un <strong>500</strong>:
 * la excepción de integridad no la manejaba nadie. Para quien usa la aplicación,
 * «error inesperado» y «ese RUC ya está registrado» no se parecen en nada.
 *
 * <p>Nótese que ya no se comprueba el código HTTP aquí: las excepciones de
 * dominio no lo llevan dentro. Esa traducción vive en
 * {@code ManejadorGlobalErrores} y es asunto de la capa web.
 */
class TraduccionRestriccionesIT extends PruebaIntegracion {

    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private EmpresaRepositorio empresas;

    @Autowired
    private SucursalRepositorio sucursales;

    @Test
    @DisplayName("Un RUC repetido se traduce a conflicto, no a error interno")
    void rucDuplicado() {
        // 20100000009 ya existe en los datos de ejemplo. El dígito verificador
        // es válido, así que el value object lo acepta y el choque ocurre donde
        // debe: en la restricción de la base.
        var error = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> empresas.guardar(new Empresa(
                        UUID.randomUUID(), CUENTA, new Ruc("20100000009"),
                        "OTRA EMPRESA S.A.C.", "Av. Cualquiera 1")));

        assertThat(error).as("la base debe rechazar el RUC repetido").isNotNull();

        var conflicto = TraductorRestricciones.traducir(error);

        assertThat(conflicto).isPresent();
        assertThat(conflicto.get().getCodigo()).isEqualTo("ruc_duplicado");
        assertThat(conflicto.get().getMessage()).contains("ya está registrado");
    }

    @Test
    @DisplayName("Un establecimiento con código repetido se traduce")
    void codigoDeEstablecimientoDuplicado() {
        // El código 0000 ya existe en esa empresa (datos de ejemplo).
        var error = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> sucursales.guardar(new Sucursal(
                        UUID.randomUUID(), UUID.fromString(EMPRESA_ADMINISTRADA), "0000",
                        "Duplicada", "Cualquier sitio")));

        assertThat(error).isNotNull();

        var conflicto = TraductorRestricciones.traducir(error);

        assertThat(conflicto).isPresent();
        assertThat(conflicto.get().getCodigo()).isEqualTo("codigo_duplicado");
    }

    // La prueba del disparador de «último administrador» se movió a
    // AdministradorDeCuentaIT, que es donde vive ese invariante. Aquí solo se
    // comprueba la traducción, no la regla.
}
