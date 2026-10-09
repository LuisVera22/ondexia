package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.application.identidad.PermisosEfectivos;
import com.ondexia.domain.identidad.ModulosContratadosRepositorio;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Que un módulo no contratado cierre la puerta en el servidor.
 *
 * <h2>Por qué esta prueba va antes que el panel</h2>
 *
 * <p>El doc 09 §7 pone la comprobación en la entrega 2 y la pantalla que la
 * manipula en la 5, y el orden es a propósito: al revés se construye un panel que
 * promete un control que el backend todavía no aplica. Aquí no hay panel; se
 * escriben las filas a mano y se comprueba que la API responde 403.
 *
 * <p>Lo que se verifica no es que el menú se oculte —eso es presentación— sino que
 * <strong>la petición se rechace aunque el rol tenga el permiso</strong>. Son dos
 * fronteras distintas: el rol dice qué puede hacer esta persona, el contrato dice
 * qué está disponible para su cuenta.
 */
class ModulosContratadosIT extends PruebaIntegracion {

    private static final String ALMACENES = "/api/v1/almacen/almacenes";
    private static final UUID CUENTA_DEMO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PermisosEfectivos permisosEfectivos;

    @Autowired
    private ModulosContratadosRepositorio contratados;

    @AfterEach
    void devolverTodoAlSitio() {
        jdbc.update("delete from cuenta_modulo");
        permisosEfectivos.vaciarCache();
    }

    /**
     * Escribe la decisión y borra la caché.
     *
     * <p>En producción la invalidación la hace {@code cuenta.permisos_version}, que
     * el panel incrementará al guardar. Aquí se vacía a mano para no depender de
     * ese incremento, que todavía no existe: si esta prueba dependiera de él,
     * estaría probando dos cosas y fallaría por la que no le toca.
     */
    private void decidir(String codigoDePermiso, boolean habilitado) {
        jdbc.update("""
                insert into cuenta_modulo (cuenta_id, permiso_id, nivel, habilitado, motivo)
                select ?, p.id, p.nivel, ?, 'prueba'
                from permiso p where p.codigo = ?
                """, CUENTA_DEMO, habilitado, codigoDePermiso);
        permisosEfectivos.vaciarCache();
    }

    @Test
    @DisplayName("sin decisiones, manda el plan y el módulo está disponible")
    void porOmisionElPlanManda() throws Exception {
        mockMvc.perform(get(ALMACENES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("apagar el módulo devuelve 403 aunque el rol tenga el permiso")
    void moduloApagadoCierraLaPuerta() throws Exception {
        decidir("almacen:acceder", false);

        mockMvc.perform(get(ALMACENES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("volver a encenderlo devuelve el acceso, sin haber tocado los roles")
    void encenderloLoDevuelve() throws Exception {
        decidir("almacen:acceder", false);
        jdbc.update("delete from cuenta_modulo");
        permisosEfectivos.vaciarCache();

        mockMvc.perform(get(ALMACENES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("por omisión los submódulos también están contratados")
    void losSubmodulosHeredanDelModulo() {
        /*
         * Esta prueba existe por un fallo concreto. La consulta calculaba el
         * contrato de todos los niveles por pertenencia a plan_modulo, que solo
         * lleva filas de MODULO: cada submódulo quedaba fuera y con él todas sus
         * funciones, de modo que la API devolvia 403 en todas partes.
         *
         * Lo peor no fue el fallo sino que la prueba del submódulo apagado PASABA,
         * porque comprobaba que un submódulo no estuviera y no estaba nunca. De ahí
         * este caso positivo: sin él, aquella pasa por el motivo equivocado.
         */
        assertThat(contratados.contratadosDe(CUENTA_DEMO))
                .as("los cuatro módulos y sus submódulos, sin ninguna decisión escrita")
                .contains("almacen", "almacen.almacen", "configuracion", "configuracion.empresa");
    }

    @Test
    @DisplayName("apagar solo el submódulo no toca el resto del módulo")
    void elSubmoduloSeApagaSolo() throws Exception {
        decidir("almacen.almacen:acceder", false);

        assertThat(contratados.contratadosDe(CUENTA_DEMO))
                .as("el módulo sigue contratado")
                .contains("almacen")
                .as("el submódulo apagado desaparece")
                .doesNotContain("almacen.almacen");
    }

    @Test
    @DisplayName("apagar el módulo se lleva sus submódulos")
    void elModuloArrastraSusSubmodulos() {
        decidir("almacen:acceder", false);

        assertThat(contratados.contratadosDe(CUENTA_DEMO))
                .as("ni el módulo ni nada que cuelgue de él")
                .noneMatch(codigo -> codigo.equals("almacen") || codigo.startsWith("almacen."));
    }

    @Test
    @DisplayName("la máscara de una cuenta no alcanza a otra que comparte el rol del sistema")
    void laMascaraNoSeFiltraEntreCuentas() {
        /*
         * Esta es la prueba que justifica que PermisosEfectivos tenga dos cachés.
         *
         * Los roles del sistema —desde la V16, solo ADMINISTRADOR— tienen
         * cuenta_id nulo: los comparten todas las cuentas. Si el recorte se
         * guardara en la caché indexada por rol, la primera cuenta en pedirlo
         * dejaría el suyo dentro y la siguiente heredaría los módulos de otra.
         *
         * Se comprueba en la capa de la consulta y no por HTTP porque no hay una
         * segunda cuenta con usuario en los datos de ejemplo. Lo que importa es
         * que el conjunto contratado dependa de la cuenta que se pregunta.
         */
        UUID otraCuenta = UUID.randomUUID();
        jdbc.update("""
                insert into cuenta (id, nombre, plan, estado_suscripcion)
                values (?, 'Otra cuenta', 'ESENCIAL', 'ACTIVA')
                """, otraCuenta);

        decidir("almacen:acceder", false);

        assertThat(contratados.contratadosDe(CUENTA_DEMO))
                .as("la cuenta que apagó el módulo no lo tiene")
                .doesNotContain("almacen");
        assertThat(contratados.contratadosDe(otraCuenta))
                .as("la otra cuenta, con el mismo plan, sí")
                .contains("almacen");

        jdbc.update("delete from cuenta where id = ?", otraCuenta);
    }

    @Test
    @DisplayName("una cuenta sin plan contratado no puede nada")
    void sinNadaContratadoNoHayPermisos() {
        for (String modulo : new String[] {"almacen", "compras", "ventas", "configuracion"}) {
            decidir(modulo + ":acceder", false);
        }

        assertThat(contratados.contratadosDe(CUENTA_DEMO))
                .as("deniega por defecto: sin módulos no queda nada que recortar")
                .isEmpty();
    }
}
