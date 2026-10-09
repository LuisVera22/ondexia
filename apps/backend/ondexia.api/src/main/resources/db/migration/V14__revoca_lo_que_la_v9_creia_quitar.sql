-- ============================================================================
-- Lo que la V9 creia quitar y no quitaba.
-- ============================================================================
--
-- Hallazgo M2 de la auditoria 2026-09-01.
--
-- La V8 concedio a ondexia_app CRUD sobre todas las tablas y, ademas, dejo
-- ALTER DEFAULT PRIVILEGES para que lo mismo se aplicara a las que se crearan
-- despues. La V9 creo cuatro tablas nuevas —plan, plan_modulo, cuenta_modulo,
-- auditoria_admin— y escribio esto:
--
--     -- Los planes son de solo lectura para la API.
--     GRANT SELECT ON plan, plan_modulo, cuenta_modulo TO ondexia_app;
--     -- La bitacora del panel no es asunto suyo, ni para leer.
--
-- El comentario describe una restriccion; la sentencia no restringe nada. GRANT
-- es ADITIVO: concede SELECT sobre lo que ya tenia INSERT, UPDATE y DELETE por
-- los privilegios por omision. Y sobre auditoria_admin no se escribio ninguna
-- sentencia — solo un comentario diciendo que no era asunto suyo, mientras la
-- aplicacion tenia CRUD completo sobre la bitacora del panel.
--
-- El efecto real: un fallo en la API de clientes podia subir su cuenta de plan,
-- activarse modulos que no ha contratado, o borrar el rastro de lo que el
-- personal hizo en el panel.
--
-- ── Por que los roles se crean tambien fuera de RDS ──────────────────────────
--
-- La V8 se salta su bloque entero cuando no existe rds_iam, que es el caso del
-- PostgreSQL de Testcontainers. Consecuencia: en las pruebas los roles NO
-- EXISTIAN, asi que ninguna prueba podia mirar lo que tenian concedido — y por
-- eso este fallo sobrevivio a una suite de 171 pruebas.
--
-- Aqui se crean siempre. Sin LOGIN ni rds_iam fuera de RDS: no hace falta que
-- nadie se conecte con ellos para comprobar sus privilegios.
--
-- Lo comprueba GrantsDeLaAplicacionIT.

DO $$
DECLARE
    es_rds boolean := EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rds_iam');
BEGIN
    -- ── Los roles ───────────────────────────────────────────────────────────
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ondexia_app') THEN
        IF es_rds THEN
            CREATE ROLE ondexia_app LOGIN;
            GRANT rds_iam TO ondexia_app;
        ELSE
            CREATE ROLE ondexia_app;
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ondexia_panel') THEN
        IF es_rds THEN
            CREATE ROLE ondexia_panel LOGIN;
            GRANT rds_iam TO ondexia_panel;
        ELSE
            CREATE ROLE ondexia_panel;
        END IF;
    END IF;

    /*
     * Las concesiones base, otra vez y de forma idempotente. En RDS ya estan
     * desde la V8; fuera de RDS no habia ninguna, y sin ellas la prueba de
     * privilegios compararia dos conjuntos vacios y pasaria siempre.
     */
    GRANT USAGE ON SCHEMA public TO ondexia_app;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ondexia_app;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ondexia_app;
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO ondexia_app;

    -- ── Lo que la V9 queria decir ───────────────────────────────────────────

    -- Los planes se leen para saber los limites, no para cambiarlos.
    REVOKE ALL ON plan, plan_modulo, cuenta_modulo FROM ondexia_app;
    GRANT SELECT ON plan, plan_modulo, cuenta_modulo TO ondexia_app;

    -- La bitacora del panel: ni leer. Es el registro de lo que hace NUESTRO
    -- personal sobre las cuentas, y una API de clientes que puede borrarlo
    -- convierte esa bitacora en una sugerencia.
    REVOKE ALL ON auditoria_admin FROM ondexia_app;

    -- Solo el contador de version de permisos, como ya intentaba la V9. Esa si
    -- funcionaba: REVOKE seguido de GRANT sobre columnas.
    REVOKE UPDATE ON cuenta FROM ondexia_app;
    GRANT UPDATE (permisos_version, actualizado_en) ON cuenta TO ondexia_app;

    /*
     * El historial de Flyway, que nadie habia mirado.
     *
     * `GRANT ... ON ALL TABLES` lo incluye, asi que la aplicacion podia reescribir
     * el registro de que migraciones se han aplicado. Con eso se puede hacer que
     * una migracion futura se salte, o que Flyway crea aplicada una que no lo
     * esta. No lo nombra el informe; sale del mismo mecanismo que M2.
     */
    REVOKE ALL ON flyway_schema_history FROM ondexia_app;
END
$$;
