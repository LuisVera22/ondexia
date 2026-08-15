package com.ondexia.admin.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ondexia.admin.pruebas.PruebaDelPanel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El listado de cuentas del panel.
 *
 * <p>Corre contra el esquema real —las migraciones de {@code ondexia.api}, que es
 * su dueño— y no contra uno inventado para la prueba. Si mañana cambia una
 * columna, esto se entera; con un esquema propio de prueba, no.
 */
class ConsultaDeCuentasIT extends PruebaDelPanel {

    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-9000-000000000001");

    private static final UUID TITULAR = UUID.fromString("00000000-0000-4000-9000-0000000000a1");
    private static final UUID TITULAR_GEMELA = UUID.fromString("00000000-0000-4000-9000-0000000000a2");
    private static final UUID DESACTIVADO = UUID.fromString("00000000-0000-4000-9000-0000000000a3");
    private static final UUID GEMELA = UUID.fromString("00000000-0000-4000-9000-000000000003");

    /*
     * La siembra es idempotente: reescribe lo que cambia y deja estar el resto.
     *
     * La version anterior borraba y reinsertaba, y con el administrador eso no
     * se puede. El disparador `cuenta_administrador_no_vacia` es DEFERRABLE
     * INITIALLY DEFERRED —comprueba al confirmar, para que cambiar de
     * administrador dentro de una transaccion funcione— pero aqui cada
     * sentencia va en su propia transaccion, asi que el borrado confirma con la
     * cuenta ya sin administrador y salta:
     *
     *   ERROR: La cuenta ... quedaria sin ningun administrador
     *
     * Que salte es correcto: es la garantia de que ninguna cuenta se queda sin
     * quien la gobierne. Lo que estaba mal era la prueba.
     */
    private void sembrarCuenta(UUID cuenta, String nombre, UUID titular, String correo) {
        jdbc.update("""
                insert into cuenta (id, nombre, plan, estado_suscripcion)
                values (cast(? as uuid), ?, 'ESENCIAL', 'ACTIVA')
                on conflict (id) do update
                    set nombre = excluded.nombre,
                        plan = excluded.plan,
                        estado_suscripcion = excluded.estado_suscripcion
                """, cuenta.toString(), nombre);

        jdbc.update("""
                insert into usuario (id, cuenta_id, cognito_sub, email, nombre, activo)
                values (cast(? as uuid), cast(? as uuid), ?, ?, 'Persona', true)
                on conflict (id) do update set activo = true
                """, titular.toString(), cuenta.toString(), correo, correo);

        jdbc.update("""
                insert into cuenta_administrador (id, cuenta_id, usuario_id)
                values (cast(? as uuid), cast(? as uuid), cast(? as uuid))
                on conflict (cuenta_id, usuario_id) do nothing
                """, titular.toString(), cuenta.toString(), titular.toString());
    }

    @BeforeEach
    void sembrar() {
        sembrarCuenta(CUENTA, "Distribuidora de prueba", TITULAR, "activo@ejemplo.com");

        // Un segundo usuario desactivado: el plan ESENCIAL da 2 y solo debe
        // contar el activo.
        jdbc.update("""
                insert into usuario (id, cuenta_id, cognito_sub, email, nombre, activo)
                values (cast(? as uuid), cast(? as uuid), ?, ?, 'Persona', false)
                on conflict (id) do update set activo = false
                """, DESACTIVADO.toString(), CUENTA.toString(),
                "baja@ejemplo.com", "baja@ejemplo.com");
    }

    @Test
    @DisplayName("sin token no se ve nada")
    void sinTokenNoHayCuentas() throws Exception {
        mockMvc.perform(get("/api/v1/cuentas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("la senal de vida no exige token")
    void laSaludEsPublica() throws Exception {
        mockMvc.perform(get("/salud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.componente").value("admin"));
    }

    @Test
    @DisplayName("con token del personal se listan las cuentas con su consumo")
    void listaConConsumo() throws Exception {
        mockMvc.perform(get("/api/v1/cuentas").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + CUENTA + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + CUENTA + "')].planNombre")
                        .value("Esencial"))
                // Uno activo de dos filas: el desactivado no consume plan.
                .andExpect(jsonPath("$[?(@.id == '" + CUENTA + "')].usuarios")
                        .value(1))
                .andExpect(jsonPath("$[?(@.id == '" + CUENTA + "')].limiteUsuarios")
                        .value(2))
                .andExpect(jsonPath("$[?(@.id == '" + CUENTA + "')].empresas")
                        .value(0));
    }

    @Test
    @DisplayName("dos cuentas con el mismo nombre se distinguen por su titular")
    void elNombreNoIdentificaLaCuenta() throws Exception {
        /*
         * Este es el caso que se vio en el panel: dos filas «Ondexia S.A.C.»
         * seguidas, sin nada que las distinguiera.
         *
         * RegistrarCuenta rellena `cuenta.nombre` con la razon social de la
         * PRIMERA empresa —la misma cadena con la que crea esa empresa—, y el
         * indice unico esta en `empresa.ruc`, no en la razon social. Asi que
         * dos cuentas distintas pueden llamarse igual, y una cuenta con tres
         * empresas se sigue llamando como la primera.
         *
         * Quien la identifica es su titular: el cuenta_administrador que la
         * abrio. Suspender la cuenta equivocada porque dos se llaman igual es
         * el error mas caro de esta pantalla.
         */
        // Mismo nombre que la cuenta sembrada arriba, distinto titular.
        sembrarCuenta(GEMELA, "Distribuidora de prueba", TITULAR_GEMELA, "otra@ejemplo.com");

        mockMvc.perform(get("/api/v1/cuentas").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + CUENTA + "')].titular")
                        .value("activo@ejemplo.com"))
                .andExpect(jsonPath("$[?(@.id == '" + GEMELA + "')].titular")
                        .value("otra@ejemplo.com"))
                // Y el nombre, que es el mismo en las dos, sigue ahi como dato
                // secundario: es util, pero no identifica.
                .andExpect(jsonPath("$[?(@.id == '" + GEMELA + "')].nombre")
                        .value("Distribuidora de prueba"));
    }

    @Test
    @DisplayName("cada modulo llega seguido de sus propios submodulos")
    void losSubmodulosCuelganDeSuModulo() throws Exception {
        /*
         * La pantalla sangra las filas SUBMODULO y no dibuja ninguna otra
         * relacion, asi que el ORDEN es lo unico que dice de quien cuelgan.
         *
         * Ordenando por nivel primero —todos los modulos y luego todos los
         * submodulos— los cinco de Almacen aparecian debajo de Ventas y
         * parecian suyos. Los datos eran correctos; lo que estaba mal era lo
         * que veia la persona que decide apagar un modulo.
         */
        String cuerpo = mockMvc.perform(get("/api/v1/cuentas/{id}/modulos", CUENTA).with(jwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> enOrden = JsonPath.read(cuerpo, "$[*].modulo");
        assertThat(enOrden).as("la cuenta de prueba tiene modulos que mirar").isNotEmpty();

        var cerrados = new ArrayList<String>();
        String actual = null;
        for (String modulo : enOrden) {
            if (!modulo.equals(actual)) {
                assertThat(cerrados)
                        .as("«%s» reaparece despues de otro modulo: sus filas quedan partidas",
                                modulo)
                        .doesNotContain(modulo);
                cerrados.add(modulo);
                actual = modulo;
            }
        }
    }

    @Test
    @DisplayName("el plan corporativo no tiene limite, y eso no es cero")
    void sinLimiteNoEsCero() {
        jdbc.update("update cuenta set plan = 'CORPORATIVO' where id = cast(? as uuid)",
                CUENTA.toString());

        var cuenta = jdbc.queryForObject("""
                select coalesce(c.limite_usuarios, p.max_usuarios)
                from cuenta c join plan p on p.codigo = c.plan
                where c.id = cast(? as uuid)
                """, Integer.class, CUENTA.toString());

        assertThat(cuenta)
                .as("nulo es «sin limite»; un cero significaria que no puede tener ninguno")
                .isNull();
    }
}
