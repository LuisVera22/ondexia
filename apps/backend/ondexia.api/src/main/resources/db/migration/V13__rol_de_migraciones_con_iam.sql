-- ============================================================================
-- El rol con el que se aplican las migraciones. Sin contraseña.
-- ============================================================================
--
-- Hallazgo A3 de la auditoria 2026-09-01: la Lambda de migraciones llevaba la
-- CONTRASENA MAESTRA de RDS en una variable de entorno, en texto plano. Ahi la
-- lee cualquiera con lambda:GetFunctionConfiguration, y ademas quedaba en el
-- estado de Terraform. Con ella se entra como ondexia_admin, que es el usuario
-- maestro de la instancia.
--
-- La API ya se conecta sin contrasena desde la V8 (rol ondexia_app con
-- rds_iam). Esto hace lo mismo con las migraciones.
--
-- ── Por que este rol es miembro de ondexia_admin ─────────────────────────────
--
-- Porque tiene que poder ALTERAR y BORRAR objetos que ya existen, y esos son
-- propiedad de ondexia_admin: sin la pertenencia, un ALTER TABLE sobre una
-- tabla creada por la V1 falla con «must be owner of table».
--
-- Conviene ser honesto sobre lo que esto compra y lo que no. NO reduce
-- privilegios: quien controle esta Lambda puede hacer lo que el maestro. Lo que
-- cambia es la credencial — pasa de un secreto estatico que vive en dos sitios
-- a un token de quince minutos firmado localmente con el rol de la funcion. Ya
-- no hay nada que leer ni nada que rotar.
--
-- ── Por que hace falta crearlo a mano la primera vez ─────────────────────────
--
-- Circulo: esta migracion crea el rol con el que se conecta la Lambda que la
-- ejecuta. En un entorno nuevo, el rol tiene que existir ANTES del primer
-- despliegue, y por eso el mismo SQL esta en
-- ondexia.infra/bootstrap/rol-migraciones.sql, para ejecutarlo una vez como
-- maestro. Esta copia versionada existe para que el estado quede registrado y
-- para mantener las concesiones al dia si cambian.
--
-- No se puede codificar en Terraform: el proveedor de PostgreSQL necesitaria
-- alcanzar la instancia por red, y RDS no es publica.

DO $$
BEGIN
    /*
     * rds_iam solo existe en RDS. En el PostgreSQL del contenedor —local y
     * pruebas de integracion— no esta, y sin esta guarda toda la suite muere
     * con «role rds_iam does not exist». Mismo criterio que la V8.
     */
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rds_iam') THEN
        RAISE NOTICE 'Sin rds_iam: no es RDS, se omite el rol de migraciones';
        RETURN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ondexia_migraciones') THEN
        CREATE ROLE ondexia_migraciones LOGIN;
    END IF;

    -- Le quita la autenticacion por contrasena, que es justamente lo que se
    -- busca: este rol no puede tener una.
    GRANT rds_iam TO ondexia_migraciones;

    -- Poder tocar lo que ya existe. Ver la nota de arriba sobre que compra.
    GRANT ondexia_admin TO ondexia_migraciones;

    EXECUTE 'GRANT CONNECT ON DATABASE ' || quote_ident(current_database())
        || ' TO ondexia_migraciones';
    GRANT USAGE, CREATE ON SCHEMA public TO ondexia_migraciones;
END
$$;
