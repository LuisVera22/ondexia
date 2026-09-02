package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lo que {@code ondexia_app} tiene concedido, contra la lista de lo que debe
 * tener.
 *
 * <h2>Por qué esta prueba existe</h2>
 *
 * <p>Hallazgo M2 de la auditoría 2026-09-01. La V9 escribió esto:
 *
 * <pre>
 *   -- Los planes son de solo lectura para la API.
 *   GRANT SELECT ON plan, plan_modulo, cuenta_modulo TO ondexia_app;
 *   -- La bitacora del panel no es asunto suyo, ni para leer.
 * </pre>
 *
 * <p>El comentario describe una restricción; la sentencia no restringe nada.
 * {@code GRANT} es <strong>aditivo</strong>, y esas cuatro tablas ya tenían CRUD
 * completo por los privilegios por omisión que dejó la V8. Sobre
 * {@code auditoria_admin} ni siquiera se escribió una sentencia: solo el
 * comentario.
 *
 * <p>Y sobrevivió a 171 pruebas porque en el contenedor los roles no existían
 * —la V8 se salta su bloque sin {@code rds_iam}—, así que no había nada que
 * mirar. La V14 los crea también fuera de RDS para que esto se pueda comprobar.
 *
 * <h2>Se compara el conjunto ENTERO, no las excepciones</h2>
 *
 * <p>La comparación es de igualdad, no de contención. Una prueba que solo
 * verificara «no tiene DELETE sobre plan» pasaría el día que alguien le conceda
 * algo nuevo sobre otra tabla, que es exactamente la forma en que estos permisos
 * se ensanchan: nunca de golpe, siempre una línea a la vez.
 *
 * <p>Añadir una tabla obliga a tocar esta lista. Es deliberado: una tabla nueva
 * hereda CRUD por los privilegios por omisión, y esta prueba es lo que fuerza a
 * decidir si eso es lo que se quería.
 */
class GrantsDeLaAplicacionIT extends PruebaIntegracion {

    private static final Set<String> CRUD =
            Set.of("SELECT", "INSERT", "UPDATE", "DELETE");

    /**
     * Lo que NO es CRUD completo. Todo lo demás lo es.
     *
     * <p>Se escribe como excepciones y no como lista completa porque la lista
     * completa son treinta y tantas tablas cuyo permiso normal es el mismo: lo
     * que importa revisar, y lo que hay que justificar al cambiarlo, son estas
     * cuatro filas.
     */
    private static final Map<String, Set<String>> EXCEPCIONES = Map.of(
            // Los límites del plan se leen, no se cambian: si la API pudiera
            // escribirlos, un fallo suyo sube de plan a la propia cuenta.
            "plan", Set.of("SELECT"),
            "plan_modulo", Set.of("SELECT"),
            "cuenta_modulo", Set.of("SELECT"),

            // La bitácora del PANEL. Es el registro de lo que hace nuestro
            // personal sobre las cuentas; una API de clientes que puede borrarlo
            // convierte esa bitácora en una sugerencia.
            "auditoria_admin", Set.of(),

            // De `cuenta` solo se escribe el contador de versión de permisos, y
            // eso es una concesión POR COLUMNA: no aparece a nivel de tabla.
            // Lo comprueba `soloSeActualizaElContadorDePermisos`.
            "cuenta", Set.of("SELECT", "INSERT", "DELETE"),

            // El historial de Flyway. Con escritura sobre él se puede hacer que
            // una migración futura se salte, o que Flyway crea aplicada una que
            // no lo está.
            "flyway_schema_history", Set.of());

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Los privilegios de ondexia_app son exactamente los esperados")
    void privilegiosExactos() {
        Map<String, Set<String>> concedidos = concedidosA("ondexia_app");
        Map<String, Set<String>> esperados = esperadosPara(concedidos.keySet());

        assertThat(concedidos)
                .as("si sobra algo, alguien concedió de más; si falta, la aplicación "
                        + "va a fallar con «permission denied» en producción")
                .isEqualTo(esperados);
    }

    /**
     * La otra mitad de la V9, que esa sí funcionaba: {@code REVOKE} sobre la
     * tabla seguido de {@code GRANT} sobre columnas.
     */
    @Test
    @DisplayName("De cuenta solo se puede actualizar el contador de permisos")
    void soloSeActualizaElContadorDePermisos() {
        Set<String> columnas = new TreeSet<>(jdbc.queryForList("""
                select column_name
                  from information_schema.role_column_grants
                 where grantee = 'ondexia_app'
                   and table_schema = 'public'
                   and table_name = 'cuenta'
                   and privilege_type = 'UPDATE'
                """, String.class));

        assertThat(columnas).containsExactly("actualizado_en", "permisos_version");
    }

    /**
     * El rol del panel no toca los datos de los clientes.
     *
     * <p>No es un hallazgo del informe: es la propiedad que hace que el panel
     * pueda existir sin ser un segundo camino hacia las facturas. Se fija aquí
     * porque el mismo mecanismo que ensanchó los permisos de {@code ondexia_app}
     * —privilegios por omisión sobre tablas futuras— actúa sobre este rol.
     */
    @Test
    @DisplayName("El rol del panel no tiene nada sobre las tablas de documentos")
    void elPanelNoVeLosDocumentos() {
        Set<String> tablas = new TreeSet<>(jdbc.queryForList("""
                select distinct table_name
                  from information_schema.role_table_grants
                 where grantee = 'ondexia_panel'
                   and table_schema = 'public'
                """, String.class));

        assertThat(tablas)
                .as("el panel ve cuentas y planes, nunca documentos ni series")
                .doesNotContain("serie", "correlativo", "almacen", "auditoria");
    }

    private Map<String, Set<String>> concedidosA(String rol) {
        var concedidos = new TreeMap<String, Set<String>>();

        jdbc.query("""
                select table_name, privilege_type
                  from information_schema.role_table_grants
                 where grantee = ?
                   and table_schema = 'public'
                """,
                fila -> {
                    concedidos
                            .computeIfAbsent(fila.getString("table_name"), t -> new TreeSet<>())
                            .add(fila.getString("privilege_type"));
                },
                rol);

        // Las tablas sin ninguna concesión no aparecen en la vista, y tienen que
        // estar en el mapa para que la comparación de igualdad las cubra: si
        // mañana alguien le concede algo a `auditoria_admin`, esto lo ve.
        for (String tabla : EXCEPCIONES.keySet()) {
            concedidos.putIfAbsent(tabla, new TreeSet<>());
        }
        return concedidos;
    }

    private static Map<String, Set<String>> esperadosPara(Set<String> tablas) {
        var esperados = new TreeMap<String, Set<String>>();
        for (String tabla : tablas) {
            esperados.put(tabla, new TreeSet<>(EXCEPCIONES.getOrDefault(tabla, CRUD)));
        }
        return esperados;
    }
}
