package com.ondexia.api.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.ondexia.api.PruebaIntegracion;
import com.ondexia.api.comun.error.TraductorRestricciones;
import com.ondexia.domain.identidad.CuentaAdministrador;
import com.ondexia.domain.identidad.CuentaAdministradorRepository;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.EmpresaRepository;
import com.ondexia.domain.identidad.Sucursal;
import com.ondexia.domain.identidad.SucursalRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Que una restricción de la base llegue al cliente como un mensaje útil.
 *
 * <p>Antes de esto, registrar un RUC repetido producía un <strong>500</strong>:
 * la excepción de integridad no la manejaba nadie y caía en el saco de los
 * errores no previstos. Para quien usa la aplicación, «error inesperado» y «ese
 * RUC ya está registrado» no se parecen en nada.
 */
class TraduccionRestriccionesIT extends PruebaIntegracion {

    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private CuentaAdministradorRepository administradores;

    @Autowired
    private SucursalRepository sucursales;

    @Test
    @DisplayName("Un RUC repetido se traduce a conflicto, no a error interno")
    void rucDuplicado() {
        // El RUC 20100000001 ya existe en los datos de ejemplo.
        var error = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> empresas.saveAndFlush(new Empresa(
                        CUENTA, "20100000001", "OTRA EMPRESA S.A.C.", "Av. Cualquiera 1")));

        assertThat(error).as("la base debe rechazar el RUC repetido").isNotNull();

        var conflicto = TraductorRestricciones.traducir(error);

        assertThat(conflicto).isPresent();
        assertThat(conflicto.get().getCodigo()).isEqualTo("ruc_duplicado");
        assertThat(conflicto.get().getEstado().value()).isEqualTo(409);
        assertThat(conflicto.get().getMessage()).contains("ya está registrado");
    }

    @Test
    @DisplayName("El mensaje de un disparador también se traduce")
    void mensajeDeDisparador() {
        // Los disparadores no violan una restricción con nombre: lanzan una
        // excepción con un texto. Se reconocen por un fragmento estable de ese
        // texto, que es frágil — de ahí que exista esta prueba.
        var soloAdministrador = administradores.findByCuentaId(CUENTA).get(0);

        var error = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> administradores.deleteById(soloAdministrador.getId()));

        assertThat(error).isNotNull();

        var conflicto = TraductorRestricciones.traducir(error);

        assertThat(conflicto).isPresent();
        assertThat(conflicto.get().getCodigo()).isEqualTo("ultimo_administrador");
        assertThat(conflicto.get().getMessage()).contains("último administrador");
    }

    @Test
    @DisplayName("Un establecimiento con código repetido se traduce")
    void codigoDeEstablecimientoDuplicado() {
        // El código 0000 ya existe en esa empresa (datos de ejemplo).
        var error = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> sucursales.saveAndFlush(new Sucursal(
                        UUID.fromString(EMPRESA_ADMINISTRADA), "0000",
                        "Duplicada", "Cualquier sitio")));

        assertThat(error).isNotNull();

        var conflicto = TraductorRestricciones.traducir(error);

        assertThat(conflicto).isPresent();
        assertThat(conflicto.get().getCodigo()).isEqualTo("codigo_duplicado");
    }
}
