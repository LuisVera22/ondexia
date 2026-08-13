-- =============================================================================
-- Datos de ejemplo. SOLO perfil local.
--
-- Esta carpeta no esta en spring.flyway.locations salvo con el perfil 'local'
-- (ver application-local.yml). En dev y en prod, Flyway ni la mira.
--
-- La version es V900 y no V3 para dejar hueco: las migraciones de esquema
-- reales van a seguir numerando desde V3, y si el ejemplo ocupara ese numero
-- habria que renumerarlo cada vez — o peor, un entorno que ya lo aplico
-- rechazaria la migracion legitima que reutilizara la version.
--
-- Los identificadores son literales fijos y no generados. Asi el token de
-- desarrollo, los datos y cualquier ejemplo de la documentacion apuntan siempre
-- a lo mismo, y un `curl` copiado del README funciona sin averiguar antes que
-- UUID toco esta vez.
-- =============================================================================

-- Cuenta demo
INSERT INTO cuenta (id, nombre, plan, estado_suscripcion, permisos_version) VALUES
    ('00000000-0000-4000-8000-000000000001', 'Cuenta de demostracion', 'PROFESIONAL', 'EN_PRUEBA', 1);

-- Usuario demo. El cognito_sub es el que hay que pedirle al emisor local:
--   curl -X POST "http://localhost:8080/desarrollo/token?sub=usuario-demo"
INSERT INTO usuario (id, cuenta_id, cognito_sub, email, nombre, activo) VALUES
    ('00000000-0000-4000-8000-000000000002',
     '00000000-0000-4000-8000-000000000001',
     'usuario-demo',
     'demo@ondexia.com',
     'Usuario de demostracion',
     true);

INSERT INTO cuenta_administrador (id, cuenta_id, usuario_id) VALUES
    ('00000000-0000-4000-8000-000000000003',
     '00000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000002');

-- Dos empresas a proposito, no una.
--
-- Con una sola empresa, el selector no aparece y la cabecera X-Empresa-Id se
-- rellena sola — con lo que todo el mecanismo multiempresa queda sin ejercitar
-- en desarrollo y el primer cliente con dos RUC descubre los fallos. Con dos, el
-- caso normal del desarrollo es el caso dificil.
INSERT INTO empresa (id, cuenta_id, ruc, razon_social, nombre_comercial,
                     domicilio_fiscal, ubigeo, modo_sunat, activo) VALUES
    ('00000000-0000-4000-8000-000000000010',
     '00000000-0000-4000-8000-000000000001',
     '20100000009', 'COMERCIAL DEMO S.A.C.', 'Demo',
     'Av. Siempre Viva 742, Lima', '150101', 'BETA', true),
    ('00000000-0000-4000-8000-000000000011',
     '00000000-0000-4000-8000-000000000001',
     '20100000017', 'DISTRIBUIDORA DEMO E.I.R.L.', 'Demo Distribucion',
     'Jr. Union 100, Lima', '150101', 'BETA', true);

INSERT INTO sucursal (id, empresa_id, codigo, nombre, direccion, ubigeo, activo) VALUES
    ('00000000-0000-4000-8000-000000000020',
     '00000000-0000-4000-8000-000000000010',
     '0000', 'Principal', 'Av. Siempre Viva 742, Lima', '150101', true),
    ('00000000-0000-4000-8000-000000000021',
     '00000000-0000-4000-8000-000000000010',
     '0001', 'Miraflores', 'Av. Larco 500, Miraflores', '150122', true),
    ('00000000-0000-4000-8000-000000000022',
     '00000000-0000-4000-8000-000000000011',
     '0000', 'Principal', 'Jr. Union 100, Lima', '150101', true);

-- Administrador en la primera empresa, con acceso a todas sus sucursales.
--
-- Ser administrador de la CUENTA no da acceso a las empresas: concede quien
-- concede, y para operar hay que asignarse como cualquiera. De ahi que esta
-- fila haga falta aunque el usuario ya sea cuenta_administrador.
INSERT INTO usuario_empresa (id, usuario_id, empresa_id, rol_id, sucursal_id)
SELECT '00000000-0000-4000-8000-000000000030',
       '00000000-0000-4000-8000-000000000002',
       '00000000-0000-4000-8000-000000000010',
       r.id,
       NULL
FROM rol r WHERE r.cuenta_id IS NULL AND r.codigo = 'ADMINISTRADOR';

-- En la segunda, vendedor y acotado a una sola sucursal. Es el caso que hay que
-- tener delante mientras se desarrolla: el mismo usuario ve cosas distintas
-- segun la empresa activa, y en una de ellas no puede anular nada.
INSERT INTO usuario_empresa (id, usuario_id, empresa_id, rol_id, sucursal_id)
SELECT '00000000-0000-4000-8000-000000000031',
       '00000000-0000-4000-8000-000000000002',
       '00000000-0000-4000-8000-000000000011',
       r.id,
       '00000000-0000-4000-8000-000000000022'
FROM rol r WHERE r.cuenta_id IS NULL AND r.codigo = 'VENDEDOR';
