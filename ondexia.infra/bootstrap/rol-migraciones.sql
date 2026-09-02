-- El rol con el que la Lambda de migraciones se conecta. Se ejecuta UNA VEZ por
-- entorno, como usuario maestro, antes del primer despliegue.
--
-- Hallazgo A3 de la auditoria 2026-09-01. El circulo que obliga a hacerlo a
-- mano: esta sentencia crea el rol con el que se conecta la funcion que aplica
-- las migraciones, asi que no puede aplicarla ella misma. Es el mismo caso que
-- los parametros de SSM de consultas.
--
-- Como ejecutarlo, desde tu equipo:
--
--   1. ondexia.infra/entornos/<entorno>.tfvars, acceso_bd_publico = true
--   2. terraform apply   (desde el EQUIPO, no desde CI: la regla se ata a tu IP)
--   3. la contrasena maestra sale del estado:
--        terraform output -json 2>/dev/null | jq -r .   # no la publica
--        terraform state show random_password.bd        # tampoco
--      Se lee con:
--        terraform show -json | jq -r '.values.root_module.resources[]
--          | select(.address=="random_password.bd") | .values.result'
--   4. psql "host=<endpoint> dbname=ondexia user=ondexia_admin sslmode=verify-full" -f rol-migraciones.sql
--   5. acceso_bd_publico = false y volver a aplicar (puede necesitar dos
--      intentos: quitar la puerta de enlace y liberar la IP publica son dos
--      cambios que Terraform no secuencia entre si)
--
-- El contenido es identico a la migracion V13, que lo mantiene al dia despues.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rds_iam') THEN
        RAISE EXCEPTION 'Esto es para RDS: no existe el rol rds_iam';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ondexia_migraciones') THEN
        CREATE ROLE ondexia_migraciones LOGIN;
    END IF;

    GRANT rds_iam TO ondexia_migraciones;
    GRANT ondexia_admin TO ondexia_migraciones;

    EXECUTE 'GRANT CONNECT ON DATABASE ' || quote_ident(current_database())
        || ' TO ondexia_migraciones';
    GRANT USAGE, CREATE ON SCHEMA public TO ondexia_migraciones;
END
$$;
