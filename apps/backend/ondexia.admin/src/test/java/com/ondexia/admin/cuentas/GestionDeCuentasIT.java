package com.ondexia.admin.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.admin.pruebas.PruebaDelPanel;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Los cambios que el panel hace sobre una cuenta.
 *
 * <p>Lo que se comprueba aquí no es solo que la fila cambie, sino las dos cosas
 * que hacen que el cambio <strong>surta efecto</strong> y <strong>deje
 * rastro</strong>: que se incremente {@code permisos_version} —o el cliente
 * seguiría entrando hasta que Lambda reciclara el contenedor— y que quede la fila
 * en {@code auditoria_admin} con quién lo hizo.
 */
class GestionDeCuentasIT extends PruebaDelPanel {

    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-9000-000000000002");
    private static final String OPERADOR = "operador@ondexia.com";

    @BeforeEach
    void sembrar() {
        jdbc.update("delete from auditoria_admin where cuenta_id = cast(? as uuid)", CUENTA.toString());
        jdbc.update("delete from cuenta_modulo where cuenta_id = cast(? as uuid)", CUENTA.toString());
        jdbc.update("delete from cuenta where id = cast(? as uuid)", CUENTA.toString());
        jdbc.update("""
                insert into cuenta (id, nombre, plan, estado_suscripcion, permisos_version)
                values (cast(? as uuid), 'Comercial de prueba', 'ESENCIAL', 'ACTIVA', 1)
                """, CUENTA.toString());
    }

    /** Con correo en el token: es lo que debe acabar en la bitácora. */
    private MockHttpServletRequestBuilder comoOperador(MockHttpServletRequestBuilder peticion) {
        return peticion.with(jwt().jwt(token -> token.claim("email", OPERADOR)))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private long version() {
        return jdbc.queryForObject(
                "select permisos_version from cuenta where id = cast(? as uuid)",
                Long.class, CUENTA.toString());
    }

    private int filasDeBitacora(String accion) {
        return jdbc.queryForObject("""
                select count(*) from auditoria_admin
                where cuenta_id = cast(? as uuid) and accion = ? and actor = ?
                """, Integer.class, CUENTA.toString(), accion, OPERADOR);
    }

    @Test
    @DisplayName("cambiar de plan surte efecto, invalida la cache y deja rastro")
    void cambioDePlan() throws Exception {
        long antes = version();

        mockMvc.perform(comoOperador(put("/api/v1/cuentas/{id}/plan", CUENTA))
                        .content("{\"plan\":\"PROFESIONAL\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select plan from cuenta where id = cast(? as uuid)",
                String.class, CUENTA.toString())).isEqualTo("PROFESIONAL");
        assertThat(version())
                .as("sin esto el cliente seguiria con los permisos del plan viejo")
                .isGreaterThan(antes);
        assertThat(filasDeBitacora("CAMBIO_DE_PLAN")).isEqualTo(1);
    }

    @Test
    @DisplayName("un plan inexistente se rechaza y no toca nada")
    void planInexistente() throws Exception {
        mockMvc.perform(comoOperador(put("/api/v1/cuentas/{id}/plan", CUENTA))
                        .content("{\"plan\":\"REGALADO\"}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select plan from cuenta where id = cast(? as uuid)",
                String.class, CUENTA.toString())).isEqualTo("ESENCIAL");
    }

    @Test
    @DisplayName("suspender guarda el motivo, y no borra nada")
    void suspension() throws Exception {
        mockMvc.perform(comoOperador(put("/api/v1/cuentas/{id}/estado", CUENTA))
                        .content("{\"estado\":\"SUSPENDIDA\",\"motivo\":\"Factura de agosto impaga\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select estado_suscripcion from cuenta where id = cast(? as uuid)",
                String.class, CUENTA.toString())).isEqualTo("SUSPENDIDA");
        assertThat(jdbc.queryForObject("""
                select despues ->> 'motivo' from auditoria_admin
                where cuenta_id = cast(? as uuid) and accion = 'CAMBIO_DE_ESTADO'
                """, String.class, CUENTA.toString()))
                .isEqualTo("Factura de agosto impaga");
    }

    @Test
    @DisplayName("apagar un modulo escribe la decision e invalida la cache")
    void apagarModulo() throws Exception {
        UUID almacen = jdbc.queryForObject(
                "select id from permiso where codigo = 'almacen:acceder'",
                UUID.class);
        long antes = version();

        mockMvc.perform(comoOperador(put("/api/v1/cuentas/{id}/modulos", CUENTA))
                        .content("{\"permisoId\":\"" + almacen
                                + "\",\"habilitado\":false,\"motivo\":\"No contratado\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("""
                select habilitado from cuenta_modulo
                where cuenta_id = cast(? as uuid) and permiso_id = ?
                """, Boolean.class, CUENTA.toString(), almacen)).isFalse();
        assertThat(version()).isGreaterThan(antes);
    }

    @Test
    @DisplayName("sin habilitado se borra la decision y vuelve a mandar el plan")
    void quitarLaDecision() throws Exception {
        UUID almacen = jdbc.queryForObject(
                "select id from permiso where codigo = 'almacen:acceder'", UUID.class);

        mockMvc.perform(comoOperador(put("/api/v1/cuentas/{id}/modulos", CUENTA))
                .content("{\"permisoId\":\"" + almacen + "\",\"habilitado\":false}"));

        mockMvc.perform(comoOperador(put("/api/v1/cuentas/{id}/modulos", CUENTA))
                        .content("{\"permisoId\":\"" + almacen + "\",\"habilitado\":null}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("""
                select count(*) from cuenta_modulo
                where cuenta_id = cast(? as uuid) and permiso_id = ?
                """, Integer.class, CUENTA.toString(), almacen))
                .as("sin fila, la cuenta hereda lo que diga su plan")
                .isZero();
    }

    @Test
    @DisplayName("no se contrata una funcion suelta")
    void nadaDeFuncionesSueltas() throws Exception {
        UUID funcion = jdbc.queryForObject(
                "select id from permiso where nivel = 'FUNCION' limit 1", UUID.class);

        mockMvc.perform(comoOperador(put("/api/v1/cuentas/{id}/modulos", CUENTA))
                        .content("{\"permisoId\":\"" + funcion + "\",\"habilitado\":false}"))
                .andExpect(status().isBadRequest());
    }
}
