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

    @BeforeEach
    void sembrar() {
        jdbc.update("delete from usuario where cuenta_id = cast(? as uuid)", CUENTA.toString());
        jdbc.update("delete from cuenta where id = cast(? as uuid)", CUENTA.toString());

        jdbc.update("""
                insert into cuenta (id, nombre, plan, estado_suscripcion)
                values (cast(? as uuid), 'Distribuidora de prueba', 'ESENCIAL', 'ACTIVA')
                """, CUENTA.toString());

        // Dos usuarios, uno desactivado: el plan ESENCIAL da 2 y solo debe
        // contar el activo.
        for (var datos : new String[][] {{"activo@ejemplo.com", "true"}, {"baja@ejemplo.com", "false"}}) {
            jdbc.update("""
                    insert into usuario (id, cuenta_id, cognito_sub, email, nombre, activo)
                    values (gen_random_uuid(), cast(? as uuid), ?, ?, 'Persona', cast(? as boolean))
                    """, CUENTA.toString(), datos[0], datos[0], datos[1]);
        }
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
                .andExpect(jsonPath("$[?(@.nombre == 'Distribuidora de prueba')]").exists())
                .andExpect(jsonPath("$[?(@.nombre == 'Distribuidora de prueba')].planNombre")
                        .value("Esencial"))
                // Uno activo de dos filas: el desactivado no consume plan.
                .andExpect(jsonPath("$[?(@.nombre == 'Distribuidora de prueba')].usuarios")
                        .value(1))
                .andExpect(jsonPath("$[?(@.nombre == 'Distribuidora de prueba')].limiteUsuarios")
                        .value(2))
                .andExpect(jsonPath("$[?(@.nombre == 'Distribuidora de prueba')].empresas")
                        .value(0));
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
