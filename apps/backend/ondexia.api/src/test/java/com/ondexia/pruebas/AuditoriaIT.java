package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.pruebas.PruebaIntegracion;
import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import com.ondexia.domain.auditoria.Anotacion;
import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La bitácora, que hasta ahora era una tabla que nadie escribía.
 */
class AuditoriaIT extends PruebaIntegracion {

    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID USUARIO = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID EMPRESA = UUID.fromString(EMPRESA_ADMINISTRADA);

    @Autowired
    private RegistroDeAuditoria auditoria;

    @Autowired
    private RegistroDeAuditoria auditorias;

    // Un solo bean: el puerto es el mismo objeto para escribir y para leer.

    @Autowired
    private TransactionTemplate transacciones;

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    private record Establecimiento(String codigo, String nombre) {
    }

    @Test
    @DisplayName("Registra quién cambió qué, con el estado antes y después")
    void registraElCambioCompleto() {
        ContextoDePrueba.comoUsuarioDe(USUARIO, CUENTA, EMPRESA);
        UUID entidadId = UUID.randomUUID();

        transacciones.executeWithoutResult(estado -> auditoria.registrarActualizacion(
                "sucursal", entidadId,
                new Establecimiento("0001", "Miraflores"),
                new Establecimiento("0001", "Miraflores Centro")));

        List<Anotacion> registros = auditorias.historialDe(EMPRESA, "sucursal", entidadId);

        assertThat(registros).hasSize(1);
        Anotacion registro = registros.get(0);

        assertThat(registro.usuarioId()).isEqualTo(USUARIO);
        assertThat(registro.accion()).isEqualTo(Anotacion.ACTUALIZAR);
        assertThat(registro.ip()).isEqualTo("127.0.0.1");
        // El documento completo, no un diff: dentro de cinco años el código que
        // calculó el diff no existe, pero el JSON crudo se sigue leyendo.
        assertThat(registro.datosAntes()).contains("Miraflores").doesNotContain("Centro");
        assertThat(registro.datosDespues()).contains("Miraflores Centro");
    }

    @Test
    @DisplayName("Si el cambio se deshace, la anotación se deshace con él")
    void laBitacoraSigueLaSuerteDeLaTransaccion() {
        ContextoDePrueba.comoUsuarioDe(USUARIO, CUENTA, EMPRESA);
        UUID entidadId = UUID.randomUUID();

        // Es la propiedad que hace que la bitácora sirva de algo. Una que
        // registra cambios que nunca ocurrieron miente igual que una que omite
        // los que sí ocurrieron.
        assertThatThrownBy(() -> transacciones.executeWithoutResult(estado -> {
            auditoria.registrarCreacion("sucursal", entidadId, new Establecimiento("0002", "Ate"));
            throw new IllegalStateException("el cambio de negocio falló");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(auditorias.historialDe(
                EMPRESA, "sucursal", entidadId))
                .as("no debe quedar rastro de un cambio que se deshizo")
                .isEmpty();
    }

    @Test
    @DisplayName("Fuera de una transacción se niega a escribir")
    void exigeTransaccionAbierta() {
        ContextoDePrueba.comoUsuarioDe(USUARIO, CUENTA, EMPRESA);

        // Propagation.MANDATORY. Escribir en su propia transacción dejaría la
        // anotación viva aunque el cambio se deshiciera, que es justo lo que la
        // prueba anterior descarta. Fallar aquí obliga a quien llame a hacerlo
        // desde dentro del cambio.
        assertThatThrownBy(() -> auditoria.registrarCreacion(
                "sucursal", UUID.randomUUID(), new Establecimiento("0003", "Surco")))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("Un objeto que no se puede serializar no bloquea la operación")
    void unFalloDeSerializacionNoTumbaElCambio() {
        ContextoDePrueba.comoUsuarioDe(USUARIO, CUENTA, EMPRESA);
        UUID entidadId = UUID.randomUUID();

        // Es la única concesión del servicio: impedir que se guarde un
        // establecimiento porque uno de sus campos no supo convertirse a JSON
        // sería dejar que la bitácora bloquee la operación que documenta.
        transacciones.executeWithoutResult(estado ->
                auditoria.registrarCreacion("sucursal", entidadId, new Object() {
                    @SuppressWarnings("unused")
                    public String getRoto() {
                        throw new UnsupportedOperationException("no se puede leer");
                    }
                }));

        List<Anotacion> registros = auditorias.historialDe(EMPRESA, "sucursal", entidadId);

        assertThat(registros).hasSize(1);
        assertThat(registros.get(0).datosDespues()).contains("no serializable");
    }
}
