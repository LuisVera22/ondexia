-- =============================================================================
-- V25 · Los privilegios que las tablas nuevas no heredaron
-- =============================================================================
--
-- Desplegado en dev el 2026-09-08, el panel, ventas y almacen respondian 500:
--
--   ERROR: permission denied for table caja
--   ERROR: permission denied for table producto
--   ERROR: permission denied for table documento_venta
--   ERROR: permission denied for table cliente
--
-- La linea divisoria era exacta: fallaba todo lo que nacio de la V17 a la V22 y
-- funcionaba todo lo anterior.
--
-- POR QUE. Los privilegios de `ondexia_app` sobre una tabla nueva llegan por dos
-- vias, y las dos se quedaron cortas:
--
--   1. La V14 hace `GRANT ... ON ALL TABLES`, que alcanza a las tablas que
--      existian EN ESE MOMENTO. Las de la V17 en adelante no existian.
--
--   2. La V8 deja privilegios por omision para las futuras, pero atados a un
--      rol concreto:
--
--        ALTER DEFAULT PRIVILEGES FOR ROLE ondexia_admin IN SCHEMA public
--            GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ondexia_app;
--
--      En PostgreSQL los privilegios por omision NO son del esquema: son del rol
--      que CREA el objeto. Y el hallazgo A3 cambio quien ejecuta Flyway — de
--      `ondexia_admin` con la contrasena maestra a `ondexia_migraciones` con
--      token de IAM—. Desde entonces las tablas nacen con otro dueño y esa
--      clausula no las alcanza.
--
-- Es decir: un arreglo correcto invalido una premisa que vivia en otro archivo.
-- Ninguno de los dos era incorrecto por separado.
--
-- ESTA MIGRACION HACE DOS COSAS
--
--   a) Concede sobre las tablas que hoy no tienen NADA. Solo sobre esas, y no un
--      `GRANT ON ALL`: un GRANT general volveria a abrir lo que la V14 revoco a
--      proposito —planes, bitacora del panel, historial de Flyway— y obligaria a
--      repetir aqui esa lista de excepciones, que es como se desincroniza.
--
--   b) Repite los privilegios por omision para `ondexia_migraciones`, para que
--      las tablas de la V26 en adelante los hereden sin que nadie se acuerde.
--
-- Lo que lo vigila a partir de ahora es `GrantsDeLaAplicacionIT`, que hasta hoy
-- no podia verlo: comparaba los privilegios concedidos contra una lista de
-- esperados DERIVADA de los propios concedidos, asi que una tabla sin ningun
-- privilegio no aparecia en ninguno de los dos lados y la igualdad se cumplia.
-- Ahora recorre todas las tablas del esquema.
-- =============================================================================

DO $$
DECLARE
    tabla text;
    reparadas text[] := '{}';
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ondexia_app') THEN
        RAISE EXCEPTION 'No existe ondexia_app: la V14 tenia que haberlo creado.';
    END IF;

    FOR tabla IN
        SELECT c.relname
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public'
           AND c.relkind = 'r'
           -- Las dos que estan vacias A PROPOSITO (V14). Sin excluirlas, esta
           -- migracion desharia justo el hallazgo M2.
           AND c.relname NOT IN ('auditoria_admin', 'flyway_schema_history')
           AND NOT EXISTS (
               SELECT 1
                 FROM information_schema.role_table_grants g
                WHERE g.grantee = 'ondexia_app'
                  AND g.table_schema = 'public'
                  AND g.table_name = c.relname)
         ORDER BY c.relname
    LOOP
        EXECUTE format(
            'GRANT SELECT, INSERT, UPDATE, DELETE ON public.%I TO ondexia_app', tabla);
        reparadas := reparadas || tabla;
    END LOOP;

    IF array_length(reparadas, 1) IS NULL THEN
        RAISE NOTICE 'Ninguna tabla estaba sin privilegios.';
    ELSE
        RAISE NOTICE 'Privilegios concedidos sobre: %', array_to_string(reparadas, ', ');
    END IF;

    -- Las secuencias, por lo mismo. Hoy el esquema usa UUID y no hay ninguna,
    -- pero la clausula de la V8 tambien las cubria y omitirla aqui dejaria el
    -- mismo hueco medio tapado.
    EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ondexia_app';
END $$;


-- -----------------------------------------------------------------------------
-- Para las tablas que vengan
-- -----------------------------------------------------------------------------
--
-- El mismo bloque de la V8, ahora tambien para el rol que ejecuta las
-- migraciones desde el hallazgo A3. Se dejan LAS DOS clausulas: la de
-- `ondexia_admin` sigue valiendo para quien aplique a mano con la contrasena
-- maestra, que es la salida de emergencia documentada en la V8.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ondexia_migraciones') THEN
        ALTER DEFAULT PRIVILEGES FOR ROLE ondexia_migraciones IN SCHEMA public
            GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ondexia_app;
        ALTER DEFAULT PRIVILEGES FOR ROLE ondexia_migraciones IN SCHEMA public
            GRANT USAGE, SELECT ON SEQUENCES TO ondexia_app;
        ALTER DEFAULT PRIVILEGES FOR ROLE ondexia_migraciones IN SCHEMA public
            GRANT EXECUTE ON FUNCTIONS TO ondexia_app;
    END IF;
END $$;
