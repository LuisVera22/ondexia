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

-- La primera empresa tiene la emision electronica configurada: usuario SOL de
-- la beta de SUNAT (MODDATOS) y un certificado dado por cargado y verificado.
-- Sin esto, el punto de venta rechazaria boletas y facturas con
-- emision_no_configurada (doc 14 §4) y las pruebas del mostrador no tendrian
-- nada que emitir. El .pfx en si no existe: en local el bus es en memoria y
-- nadie lo abre.
UPDATE empresa
   SET usuario_sol = 'MODDATOS',
       certificado_cargado_en = now(),
       certificado_verificado_en = now(),
       certificado_sujeto = 'CN=COMERCIAL DEMO S.A.C. (certificado de ejemplo)',
       certificado_vence_en = date '2030-12-31'
 WHERE id = '00000000-0000-4000-8000-000000000010';

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

-- El almacen de cada establecimiento, como lo deja el alta desde la iteracion 2
-- (DotacionDeEstablecimiento): el punto de venta descarga de aqui. Bajo RLS.
SELECT set_config('ondexia.empresa_id', '00000000-0000-4000-8000-000000000010', true);
INSERT INTO almacen (id, empresa_id, sucursal_id, codigo, nombre) VALUES
    ('00000000-0000-4000-8000-000000000090', '00000000-0000-4000-8000-000000000010',
     '00000000-0000-4000-8000-000000000020', 'PRINCIPAL', 'Almacen principal'),
    ('00000000-0000-4000-8000-000000000091', '00000000-0000-4000-8000-000000000010',
     '00000000-0000-4000-8000-000000000021', 'ALM-0001', 'Almacen Miraflores');
-- Series del mostrador de la matriz (iteracion 4): la nota de venta es interna
-- y las otras dos quedan PENDIENTES hasta la iteracion 5.
INSERT INTO serie_correlativo (id, empresa_id, sucursal_id, tipo_documento, serie) VALUES
    ('00000000-0000-4000-8000-0000000000a0', '00000000-0000-4000-8000-000000000010',
     '00000000-0000-4000-8000-000000000020', 'NV', 'N001'),
    ('00000000-0000-4000-8000-0000000000a1', '00000000-0000-4000-8000-000000000010',
     '00000000-0000-4000-8000-000000000020', '03', 'B001'),
    ('00000000-0000-4000-8000-0000000000a2', '00000000-0000-4000-8000-000000000010',
     '00000000-0000-4000-8000-000000000020', '01', 'F001');

SELECT set_config('ondexia.empresa_id', '00000000-0000-4000-8000-000000000011', true);
INSERT INTO almacen (id, empresa_id, sucursal_id, codigo, nombre) VALUES
    ('00000000-0000-4000-8000-000000000092', '00000000-0000-4000-8000-000000000011',
     '00000000-0000-4000-8000-000000000022', 'PRINCIPAL', 'Almacen principal');
INSERT INTO serie_correlativo (id, empresa_id, sucursal_id, tipo_documento, serie) VALUES
    ('00000000-0000-4000-8000-0000000000a3', '00000000-0000-4000-8000-000000000011',
     '00000000-0000-4000-8000-000000000022', 'NV', 'N001');
SELECT set_config('ondexia.empresa_id', '', true);

-- La primera caja de cada establecimiento, como la deja el alta desde la
-- iteracion 2 (DotacionDeEstablecimiento). Los datos de ejemplo se insertan
-- sin pasar por el caso de uso, asi que se ponen a mano.
--
-- `caja` esta bajo Row Level Security FORZADO, que alcanza tambien al dueno de
-- la tabla que ejecuta esta migracion: sin fijar la empresa, la insercion se
-- rechaza. Se fija por empresa, local a la transaccion de Flyway, y se limpia
-- al final para que nada de lo que venga despues herede una empresa.
SELECT set_config('ondexia.empresa_id', '00000000-0000-4000-8000-000000000010', true);
INSERT INTO caja (id, empresa_id, sucursal_id, codigo, nombre) VALUES
    ('00000000-0000-4000-8000-000000000050',
     '00000000-0000-4000-8000-000000000010',
     '00000000-0000-4000-8000-000000000020', 'CAJA1', 'Caja 1'),
    ('00000000-0000-4000-8000-000000000051',
     '00000000-0000-4000-8000-000000000010',
     '00000000-0000-4000-8000-000000000021', 'CAJA1', 'Caja 1');

SELECT set_config('ondexia.empresa_id', '00000000-0000-4000-8000-000000000011', true);
INSERT INTO caja (id, empresa_id, sucursal_id, codigo, nombre) VALUES
    ('00000000-0000-4000-8000-000000000052',
     '00000000-0000-4000-8000-000000000011',
     '00000000-0000-4000-8000-000000000022', 'CAJA1', 'Caja 1');

SELECT set_config('ondexia.empresa_id', '', true);

-- Un catalogo minimo y dos clientes en la primera empresa (iteracion 3), para
-- que el punto de venta tenga con que trabajar en desarrollo. Los productos se
-- ofrecen en la matriz; Miraflores se activa desde la ficha, como haria un
-- cliente. Bajo RLS, igual que las cajas.
SELECT set_config('ondexia.empresa_id', '00000000-0000-4000-8000-000000000010', true);
INSERT INTO producto (id, empresa_id, codigo, nombre, unidad_medida, afectacion_igv,
                      precio_lista, controla_stock) VALUES
    ('00000000-0000-4000-8000-000000000060', '00000000-0000-4000-8000-000000000010',
     'CEM-001', 'Cemento Portland Tipo I 42.5 kg', 'BG', '10', 32.500000, true),
    ('00000000-0000-4000-8000-000000000061', '00000000-0000-4000-8000-000000000010',
     'FIE-012', 'Fierro corrugado 1/2" x 9 m', 'NIU', '10', 48.000000, true),
    ('00000000-0000-4000-8000-000000000062', '00000000-0000-4000-8000-000000000010',
     'SRV-INST', 'Instalacion a domicilio', 'ZZ', '10', 80.000000, false);

INSERT INTO producto_local (id, empresa_id, producto_id, sucursal_id, disponible) VALUES
    ('00000000-0000-4000-8000-000000000070', '00000000-0000-4000-8000-000000000010',
     '00000000-0000-4000-8000-000000000060', '00000000-0000-4000-8000-000000000020', true),
    ('00000000-0000-4000-8000-000000000071', '00000000-0000-4000-8000-000000000010',
     '00000000-0000-4000-8000-000000000061', '00000000-0000-4000-8000-000000000020', true),
    ('00000000-0000-4000-8000-000000000072', '00000000-0000-4000-8000-000000000010',
     '00000000-0000-4000-8000-000000000062', '00000000-0000-4000-8000-000000000020', true);

INSERT INTO cliente (id, empresa_id, tipo_documento, numero_documento, nombre, direccion) VALUES
    ('00000000-0000-4000-8000-000000000080', '00000000-0000-4000-8000-000000000010',
     '1', '70123456', 'Juan Perez Gomez', NULL),
    ('00000000-0000-4000-8000-000000000081', '00000000-0000-4000-8000-000000000010',
     '6', '20131312955', 'SUPERINTENDENCIA NACIONAL DE ADUANAS Y DE ADMINISTRACION TRIBUTARIA',
     'AV. GARCILASO DE LA VEGA 1472, LIMA');

SELECT set_config('ondexia.empresa_id', '', true);

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

-- Dos roles A MEDIDA de la cuenta demo: Vendedor y Almacenero.
--
-- Desde la V16 el sistema solo trae ADMINISTRADOR (doc 12 §6.1); los demas los
-- crea cada cuenta. La cuenta demo los tiene porque las pruebas y el desarrollo
-- necesitan un rol que NO pueda todo, y el catalogo de permisos es el mismo
-- patron que la V2 daba a los roles retirados, mas los eslabones de modulo y
-- submodulo que la V6 derivo (sin ellos la funcion suelta no autoriza nada).
INSERT INTO rol (id, cuenta_id, codigo, nombre, descripcion) VALUES
    ('00000000-0000-4000-8000-000000000040',
     '00000000-0000-4000-8000-000000000001',
     'VENDEDOR', 'Vendedor',
     'Emite comprobantes y gestiona clientes. No anula ni toca el catalogo.'),
    ('00000000-0000-4000-8000-000000000041',
     '00000000-0000-4000-8000-000000000001',
     'ALMACENERO', 'Almacenero',
     'Gestiona catalogo, existencias y movimientos. No vende ni compra.');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT '00000000-0000-4000-8000-000000000040', p.id
FROM permiso p
WHERE p.nivel = 'FUNCION'
  AND (
        p.modulo IN ('ventas.cliente', 'ventas.cotizacion', 'ventas.nota_preventa')
     OR (p.modulo IN ('ventas.comprobante', 'ventas.nota_credito', 'ventas.nota_debito')
         AND p.accion IN ('consultar', 'registrar', 'emitir'))
     -- Abre y cierra su caja; no crea cajas ni las desactiva.
     OR (p.modulo = 'ventas.caja' AND p.accion IN ('consultar', 'abrir', 'cerrar'))
     -- Vende con nota de venta desde el mostrador.
     OR (p.modulo = 'ventas.nota_venta' AND p.accion IN ('consultar', 'registrar'))
     OR (p.modulo IN ('almacen.producto', 'almacen.marca', 'almacen.modelo',
                      'almacen.presentacion', 'almacen.precio', 'almacen.stock')
         AND p.accion = 'consultar')
  );

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT '00000000-0000-4000-8000-000000000041', p.id
FROM permiso p
WHERE p.nivel = 'FUNCION'
  AND (
        p.modulo LIKE 'almacen.%'
     OR (p.modulo IN ('compras.orden_compra', 'compras.proveedor') AND p.accion = 'consultar')
  )
  AND NOT (p.modulo = 'almacen.guia_remision' AND p.accion = 'emitir');

-- Los eslabones de la jerarquia (V6): submodulo y modulo de cada funcion.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT DISTINCT rp.rol_id, sub.id
FROM rol_permiso rp
    JOIN permiso f   ON f.id = rp.permiso_id AND f.nivel = 'FUNCION'
    JOIN permiso sub ON sub.nivel = 'SUBMODULO' AND sub.modulo = f.modulo
WHERE rp.rol_id IN ('00000000-0000-4000-8000-000000000040',
                    '00000000-0000-4000-8000-000000000041')
ON CONFLICT DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT DISTINCT rp.rol_id, m.id
FROM rol_permiso rp
    JOIN permiso f ON f.id = rp.permiso_id AND f.nivel = 'FUNCION'
    JOIN permiso m ON m.nivel = 'MODULO' AND m.modulo = split_part(f.modulo, '.', 1)
WHERE rp.rol_id IN ('00000000-0000-4000-8000-000000000040',
                    '00000000-0000-4000-8000-000000000041')
ON CONFLICT DO NOTHING;

-- En la segunda, vendedor y acotado a una sola sucursal. Es el caso que hay que
-- tener delante mientras se desarrolla: el mismo usuario ve cosas distintas
-- segun la empresa activa, y en una de ellas no puede anular nada.
INSERT INTO usuario_empresa (id, usuario_id, empresa_id, rol_id, sucursal_id) VALUES
    ('00000000-0000-4000-8000-000000000031',
     '00000000-0000-4000-8000-000000000002',
     '00000000-0000-4000-8000-000000000011',
     '00000000-0000-4000-8000-000000000040',
     '00000000-0000-4000-8000-000000000022');
