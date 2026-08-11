-- Crea el rol y la base de datos de la aplicacion.
--
-- ¿Por que no usar directamente POSTGRES_USER? Porque ese es el superusuario
-- bootstrap del cluster, y un superusuario NO esta sujeto a ninguna politica de
-- Row Level Security. Ni siquiera FORCE ROW LEVEL SECURITY le alcanza: FORCE
-- solo afecta al propietario de la tabla, no al superusuario.
--
-- Y no se puede arreglar degradandolo. PostgreSQL lo rechaza:
--
--     ALTER ROLE postgres NOSUPERUSER;
--     ERROR:  permission denied to alter role
--     DETAIL: The bootstrap superuser must have the SUPERUSER attribute.
--
-- Asi que la unica forma de que RLS proteja algo en local es que la aplicacion
-- se conecte con OTRO rol. De ahi este script:
--
--   postgres  — superusuario bootstrap. No lo usa nadie. Existe porque el
--               cluster necesita uno.
--   ondexia   — rol de la aplicacion. Sin SUPERUSER y sin BYPASSRLS, dueno de
--               su propia base de datos.
--
-- Que sea dueno de la base le basta para todo lo que hace Flyway —crear tablas,
-- funciones, disparadores, politicas— sin darle ningun privilegio de cluster. Y
-- como es el propietario de las tablas, FORCE ROW LEVEL SECURITY si le aplica,
-- que es justo lo que se busca.
--
-- Esto no es un apano de desarrollo: reproduce la forma que tiene en AWS, donde
-- el usuario maestro de RDS tampoco es el superusuario bootstrap. Desarrollar
-- contra una topologia de permisos distinta de la de produccion es como se
-- descubren estas cosas en el peor momento.

CREATE ROLE ondexia LOGIN PASSWORD 'ondexia';

CREATE DATABASE ondexia OWNER ondexia;
