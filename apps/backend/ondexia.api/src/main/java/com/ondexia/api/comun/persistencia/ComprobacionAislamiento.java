package com.ondexia.api.comun.persistencia;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Impide arrancar si el rol de la base de datos puede saltarse Row Level
 * Security.
 *
 * <h2>Por que hace falta una comprobacion para esto</h2>
 *
 * En PostgreSQL, un rol con {@code SUPERUSER} o con {@code BYPASSRLS} <strong>no
 * esta sujeto a ninguna politica</strong>, y {@code FORCE ROW LEVEL SECURITY}
 * tampoco le afecta: {@code FORCE} solo alcanza al propietario de la tabla, no
 * al superusuario.
 *
 * <p>El resultado es el peor estado posible de un control de seguridad: las
 * politicas existen, {@code pg_policies} las lista, el codigo parece correcto y
 * <strong>no filtran absolutamente nada</strong>. No hay error, no hay aviso, y
 * las consultas devuelven datos de todos los inquilinos con forma perfectamente
 * normal.
 *
 * <p>Esto no es hipotetico: la imagen oficial de PostgreSQL crea
 * {@code POSTGRES_USER} como superusuario, asi que el entorno de desarrollo por
 * omision <em>tiene</em> el problema. Se descubrio aqui porque las pruebas de
 * aislamiento fallaron; sin ellas, el sistema habria llegado a produccion
 * pareciendo protegido.
 *
 * <p>De ahi que la comprobacion viva en la aplicacion y no en la documentacion.
 * Un despliegue con el rol equivocado no arranca, que es ruidoso y se arregla en
 * minutos. La alternativa es una fuga entre clientes que nadie detecta.
 */
@Component
public class ComprobacionAislamiento implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ComprobacionAislamiento.class);

    private final JdbcTemplate jdbc;

    public ComprobacionAislamiento(DataSource origenDatos) {
        this.jdbc = new JdbcTemplate(origenDatos);
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        String rol = jdbc.queryForObject("select current_user", String.class);

        Boolean saltaPoliticas = jdbc.queryForObject(
                "select rolsuper or rolbypassrls from pg_roles where rolname = current_user",
                Boolean.class);

        if (Boolean.TRUE.equals(saltaPoliticas)) {
            throw new IllegalStateException("""

                    ==========================================================================
                      El rol de base de datos '%s' es SUPERUSER o tiene BYPASSRLS.

                      Row Level Security NO se aplica a esos roles, ni siquiera con FORCE. El
                      aislamiento multiempresa quedaria activo y sin ningun efecto: las
                      consultas devolverian datos de todos los clientes sin dar ningun error.

                      Se aborta el arranque a proposito.

                      En local:  no te conectes con POSTGRES_USER. Ese es el superusuario
                                 bootstrap del cluster y NO se le puede quitar el atributo
                                 ("the bootstrap superuser must have the SUPERUSER
                                 attribute"). La aplicacion usa el rol 'ondexia', que crea
                                 docker/initdb/10-rol-aplicacion.sql. Si la base ya existia
                                 antes de anadir ese script: docker compose down -v.
                      En AWS:    el usuario maestro de RDS no es el superusuario bootstrap,
                                 asi que esto no deberia ocurrir. Si ocurre, alguien
                                 concedio BYPASSRLS.
                    ==========================================================================
                    """.formatted(rol, rol));
        }

        // Segunda comprobacion: que las politicas existan de verdad. Un rol
        // correcto sobre una tabla sin politica tampoco filtra nada, y ese fallo
        // es igual de silencioso.
        Integer sinPolitica = jdbc.queryForObject("""
                select count(*)
                from pg_tables t
                where t.schemaname = 'public'
                  and exists (
                      select 1 from information_schema.columns c
                      where c.table_schema = t.schemaname
                        and c.table_name = t.tablename
                        and c.column_name = 'empresa_id')
                  and not exists (
                      select 1 from pg_policies p
                      where p.schemaname = t.schemaname and p.tablename = t.tablename)
                """, Integer.class);

        if (sinPolitica != null && sinPolitica > 0) {
            // Aviso y no error: hoy hay tablas con empresa_id legitimamente fuera
            // de RLS —empresa, sucursal y usuario_empresa son las que hay que leer
            // PARA saber cual es la empresa activa, asi que una politica que
            // dependa de esa respuesta las dejaria vacias siempre.
            //
            // Convertirlo en error obligaria a mantener una lista de excepciones
            // que acabaria creciendo con cualquier tabla nueva y perdiendo el
            // sentido. El aviso, en cambio, aparece en el log de cada arranque y
            // obliga a mirar si el numero sube.
            LOG.warn("Hay {} tabla(s) con columna empresa_id sin politica de RLS. "
                    + "Si no es una de las tres de resolucion de contexto (empresa, sucursal, "
                    + "usuario_empresa), falta llamar a activar_aislamiento_empresa() en su "
                    + "migracion.", sinPolitica);
        }

        LOG.info("Aislamiento multiempresa verificado: rol '{}' sujeto a las politicas de RLS.",
                rol);
    }
}
