-- =============================================================================
-- V8 — Rol de aplicacion que se autentica con IAM, sin contrasena.
--
-- Hasta ahora la Lambda de la API se conectaba como ondexia_admin con una
-- contrasena que viajaba en una variable de entorno. Ahi la lee cualquiera con
-- lambda:GetFunctionConfiguration —Lambda las devuelve descifradas a quien
-- tenga el permiso— y ademas quedaba en el estado de Terraform.
--
-- Con IAM no hay contrasena que guardar en ningun sitio. El token lo genera la
-- funcion FIRMANDO LOCALMENTE con las credenciales de su rol, sin llamada de
-- red: es el mismo mecanismo que hizo viable la subida de logos sin NAT.
--
-- POR QUE UN ROL NUEVO Y NO ondexia_admin
--
-- Porque conceder rds_iam a un rol le QUITA la autenticacion por contrasena.
-- Hacerlo sobre el usuario maestro significa que, si la ruta de IAM falla, no
-- queda forma de entrar a arreglarlo: restablecer la contrasena maestra desde
-- la API de RDS no reactiva ese metodo mientras rds_iam siga concedido, y
-- revocarlo exige conectarse. Es un candado con la llave dentro.
--
-- Asi que ondexia_admin conserva su contrasena y no la usa nadie salvo las
-- migraciones. Es la salida de emergencia.
--
-- POR QUE NO SE LE TRANSFIERE LA PROPIEDAD DE LAS TABLAS
--
-- Al reves de lo que hace el entorno local. Un rol que NO es propietario esta
-- sujeto a las politicas de RLS siempre, sin necesidad de FORCE; el propietario
-- las ignora salvo que se fuerce. Dejar a ondexia_app fuera de la propiedad
-- hace que su aislamiento no dependa de que FORCE siga puesto en cada tabla
-- futura — una capa menos de la que acordarse.
--
-- Y de paso no puede ejecutar DDL, que es correcto: el esquema lo cambian las
-- migraciones, no la aplicacion.
-- =============================================================================

DO $$
BEGIN
    /*
     * rds_iam solo existe en RDS. En el PostgreSQL del contenedor —local y
     * pruebas— no esta, y sin esta guarda la migracion reventaria ahi con un
     * «role rds_iam does not exist» que no tiene nada que ver con el codigo.
     *
     * En local la aplicacion sigue conectandose como el rol de siempre, que ya
     * es propietario de todo.
     */
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rds_iam') THEN
        RAISE NOTICE 'Sin rds_iam: no es RDS, se omite el rol de aplicacion';
        RETURN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ondexia_app') THEN
        -- Sin contrasena y sin poder crearla. LOGIN si, porque se conecta; la
        -- credencial la pone IAM en cada conexion.
        CREATE ROLE ondexia_app LOGIN;
    END IF;

    GRANT rds_iam TO ondexia_app;
END $$;


-- Privilegios. Fuera del bloque porque estas sentencias son inocuas aunque el
-- rol no exista... no lo son: fallarian. Se repite la guarda.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ondexia_app') THEN
        RETURN;
    END IF;

    EXECUTE 'GRANT CONNECT ON DATABASE ' || quote_ident(current_database())
            || ' TO ondexia_app';
    GRANT USAGE ON SCHEMA public TO ondexia_app;

    -- Lo que hace una aplicacion: leer y escribir filas. Nada de DDL.
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ondexia_app;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ondexia_app;
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO ondexia_app;

    /*
     * Y lo mismo para lo que se cree DESPUES.
     *
     * Sin esto, la primera tabla que anada una migracion futura seria invisible
     * para la aplicacion: un «permission denied for table» en produccion, en el
     * despliegue siguiente, lejos de donde se escribio la migracion. Las
     * privilegios por omision se aplican a lo que cree ondexia_admin, que es
     * quien ejecuta Flyway.
     */
    ALTER DEFAULT PRIVILEGES FOR ROLE ondexia_admin IN SCHEMA public
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ondexia_app;
    ALTER DEFAULT PRIVILEGES FOR ROLE ondexia_admin IN SCHEMA public
        GRANT USAGE, SELECT ON SEQUENCES TO ondexia_app;
    ALTER DEFAULT PRIVILEGES FOR ROLE ondexia_admin IN SCHEMA public
        GRANT EXECUTE ON FUNCTIONS TO ondexia_app;
END $$;


COMMENT ON DATABASE ondexia IS
    'La aplicacion se conecta como ondexia_app con token de IAM, sin contrasena. '
    'ondexia_admin conserva la suya y solo la usan las migraciones: es la salida '
    'de emergencia si la ruta de IAM falla.';
