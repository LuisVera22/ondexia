package com.ondexia.admin.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.admin.pruebas.PruebaDelPanel;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La bitácora del panel: quién queda escrito, y que no se pueda reescribir.
 *
 * <p>Hallazgo A6 de la auditoría 2026-09-01. Dos defectos que se refuerzan: el
 * actor se identificaba por su correo —que su dueño puede cambiar en Cognito— y
 * la tabla no tenía disparador de solo inserción, así que era la única bitácora
 * del sistema que se podía modificar.
 *
 * <p>Cada uno solo es grave con el otro delante. Un actor falsificable importa
 * poco si el registro es inmutable y se puede correlacionar; un registro
 * modificable importa poco si nadie tiene acceso de escritura. Estaban los dos.
 */
class BitacoraDelPanelIT extends PruebaDelPanel {

    @Autowired
    private Bitacora bitacora;

    @Autowired
    private JdbcTemplate jdbc;

    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-9000-0000000000a6");

    /**
     * La cuenta tiene que existir: `auditoria_admin.cuenta_id` es clave ajena.
     *
     * <p>Se siembra aqui y no se reutiliza la de ejemplo porque este modulo corre
     * sin `db/local` a proposito — cada prueba pone lo suyo, y asi se lee en la
     * prueba lo que esta contando.
     */
    @BeforeEach
    void sembrarCuenta() {
        jdbc.update("""
                insert into cuenta (id, nombre, plan, estado_suscripcion, permisos_version)
                values (cast(? as uuid), 'Cuenta de la prueba A6', 'PROFESIONAL', 'ACTIVA', 1)
                on conflict (id) do nothing
                """, CUENTA.toString());
    }

    @Test
    @DisplayName("Se guarda el sub y el correo, y el sub es lo que identifica")
    void guardaLosDos() {
        var operadora = new Operador("sub-de-cognito-inmutable", "operadora@ondexia.com");

        bitacora.registrar(CUENTA, operadora, "PRUEBA_A6", null, null);

        var fila = jdbc.queryForMap("""
                select actor_sub, actor
                  from auditoria_admin
                 where accion = 'PRUEBA_A6'
                """);

        assertThat(fila.get("actor_sub")).isEqualTo("sub-de-cognito-inmutable");
        assertThat(fila.get("actor")).isEqualTo("operadora@ondexia.com");
    }

    /**
     * Sin {@code sub} no se escribe nada.
     *
     * <p>La versión anterior ponía «desconocido» cuando el token no traía lo que
     * hacía falta. Una fila de bitácora que no señala a nadie es peor que un
     * error: parece que hay rastro.
     */
    @Test
    @DisplayName("Un operador sin sub no llega a la bitácora")
    void sinSubNoHayFila() {
        assertThatThrownBy(() -> new Operador(null, "alguien@ondexia.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Una fila de la bitácora no se puede modificar")
    void noSePuedeActualizar() {
        bitacora.registrar(CUENTA, new Operador("sub-1", "a@ondexia.com"),
                "PRUEBA_A6_UPDATE", null, null);

        assertThatThrownBy(() -> jdbc.update(
                "update auditoria_admin set actor = 'otro' where accion = 'PRUEBA_A6_UPDATE'"))
                .hasMessageContaining("solo insercion");
    }

    @Test
    @DisplayName("Ni borrar")
    void noSePuedeBorrar() {
        bitacora.registrar(CUENTA, new Operador("sub-2", "b@ondexia.com"),
                "PRUEBA_A6_DELETE", null, null);

        assertThatThrownBy(() -> jdbc.update(
                "delete from auditoria_admin where accion = 'PRUEBA_A6_DELETE'"))
                .hasMessageContaining("solo insercion");
    }
}
