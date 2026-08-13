-- =============================================================================
-- V6 — El catalogo de permisos pasa a ser jerarquico.
--
-- Antes: una lista plana de pares (modulo, accion), donde «modulo» ya era un
-- codigo con punto —almacen.producto— pero el punto no significaba nada para
-- nadie. La jerarquia existia en la cabeza de quien leia el codigo.
--
-- Ahora son tres niveles explicitos y CONCEDIBLES:
--
--   MODULO      almacen                     -> Almacen
--   SUBMODULO   almacen.producto            -> Productos
--   FUNCION     almacen.producto:consultar  -> Consultar
--
-- Y la autorizacion es CONJUNTIVA: para poder consultar productos hay que tener
-- los tres. Apagar el modulo Almacen deja fuera todo lo que cuelga de el aunque
-- sus casillas sigan marcadas, que es justamente el control que se buscaba.
--
-- Sobre el efecto en los modulos que vengan despues: un submodulo nuevo NO lo
-- hereda nadie por el hecho de tener su modulo. La herencia no ocurre al
-- evaluar, ocurre AQUI: la migracion que cree el submodulo lo concede por
-- patron —«a todo rol que tenga el modulo almacen»— en vez de enumerar roles
-- uno por uno. Es una linea de SQL por submodulo nuevo, explicita y revisable,
-- y es lo que sustituye a repasar cada rol a medida a mano.
--
-- Los nombres para mostrar viven aqui y no en el frontend. Estaban en un mapa
-- de TypeScript escrito a mano que habria que recordar ampliar con cada modulo
-- nuevo, y que al no hacerlo degrada en silencio: la pantalla muestra el codigo
-- crudo y nadie se entera hasta que un cliente pregunta que es
-- «almacen.tipo_precio».
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Nivel y nombre
-- -----------------------------------------------------------------------------

ALTER TABLE permiso ADD COLUMN nivel varchar(10);
ALTER TABLE permiso ADD COLUMN nombre varchar(120);

-- Todo lo que existe hoy es una funcion.
UPDATE permiso SET nivel = 'FUNCION' WHERE nivel IS NULL;

-- El nombre de una funcion es su accion capitalizada, salvo las que en
-- castellano no salen bien de un initcap.
UPDATE permiso SET nombre = CASE accion
    WHEN 'consultar'  THEN 'Consultar'
    WHEN 'registrar'  THEN 'Registrar'
    WHEN 'editar'     THEN 'Editar'
    WHEN 'desactivar' THEN 'Desactivar'
    WHEN 'eliminar'   THEN 'Eliminar'
    WHEN 'anular'     THEN 'Anular'
    WHEN 'emitir'     THEN 'Emitir'
    WHEN 'aprobar'    THEN 'Aprobar'
    WHEN 'exportar'   THEN 'Exportar'
    WHEN 'ajustar'    THEN 'Ajustar'
    WHEN 'trasladar'  THEN 'Trasladar'
    WHEN 'enviar'     THEN 'Enviar'
    ELSE initcap(accion)
END
WHERE nombre IS NULL;

ALTER TABLE permiso ALTER COLUMN nivel SET NOT NULL;
ALTER TABLE permiso ALTER COLUMN nombre SET NOT NULL;

ALTER TABLE permiso ADD CONSTRAINT permiso_nivel_valido
    CHECK (nivel IN ('MODULO', 'SUBMODULO', 'FUNCION'));

-- Un modulo no lleva punto; un submodulo y una funcion, si. Lo comprueba la
-- base porque de esa forma del codigo depende la evaluacion entera: el chequeo
-- conjuntivo parte el codigo por el punto para encontrar a su padre.
ALTER TABLE permiso ADD CONSTRAINT permiso_forma_del_codigo CHECK (
    CASE nivel
        WHEN 'MODULO'    THEN modulo NOT LIKE '%.%' AND accion = 'acceder'
        WHEN 'SUBMODULO' THEN modulo LIKE '%.%'     AND accion = 'acceder'
        WHEN 'FUNCION'   THEN modulo LIKE '%.%'     AND accion <> 'acceder'
    END
);

CREATE INDEX permiso_por_nivel ON permiso (nivel, modulo);

COMMENT ON COLUMN permiso.nivel IS
    'MODULO, SUBMODULO o FUNCION. La autorizacion exige los tres: tener '
    'almacen.producto:consultar no basta si falta almacen.producto:acceder o '
    'almacen:acceder.';

COMMENT ON COLUMN permiso.nombre IS
    'Como se muestra en la matriz de permisos. Vive aqui y no en el frontend '
    'para que un modulo nuevo no exija tocar dos repositorios.';


-- -----------------------------------------------------------------------------
-- 2. Los modulos
-- -----------------------------------------------------------------------------
--
-- La accion es 'acceder' para que el codigo se siga componiendo igual
-- —modulo:accion— y no haya dos formas de construirlo segun el nivel.

INSERT INTO permiso (id, codigo, modulo, accion, nivel, nombre, descripcion)
SELECT gen_random_uuid(), d.codigo || ':acceder', d.codigo, 'acceder', 'MODULO',
       d.nombre, d.descripcion
FROM (VALUES
    ('almacen',      'Almacén',       'Catálogo, existencias y movimientos de mercadería.'),
    ('compras',      'Compras',       'Proveedores, órdenes y documentos de compra.'),
    ('ventas',       'Ventas',        'Clientes, cotizaciones y comprobantes de venta.'),
    ('configuracion','Configuración', 'Datos de la empresa, usuarios, roles y series.')
) AS d(codigo, nombre, descripcion);


-- -----------------------------------------------------------------------------
-- 3. Los submodulos
-- -----------------------------------------------------------------------------
--
-- Se derivan de los modulos que ya aparecen en las funciones existentes, con su
-- nombre para mostrar. Derivarlos con un DISTINCT sin nombre habria sido mas
-- corto y habria dejado la pantalla mostrando codigos.

INSERT INTO permiso (id, codigo, modulo, accion, nivel, nombre, descripcion)
SELECT gen_random_uuid(), d.codigo || ':acceder', d.codigo, 'acceder', 'SUBMODULO',
       d.nombre, NULL
FROM (VALUES
    ('almacen.producto',        'Productos'),
    ('almacen.marca',           'Marcas'),
    ('almacen.modelo',          'Modelos'),
    ('almacen.presentacion',    'Presentaciones'),
    ('almacen.tipo_precio',     'Tipos de precio'),
    ('almacen.precio',          'Precios'),
    ('almacen.almacen',         'Almacenes'),
    ('almacen.stock',           'Existencias'),
    ('almacen.kardex',          'Kardex'),
    ('almacen.guia_ingreso',    'Guías de ingreso'),
    ('almacen.guia_remision',   'Guías de remisión'),

    ('compras.proveedor',       'Proveedores'),
    ('compras.nota_pedido',     'Notas de pedido'),
    ('compras.orden_compra',    'Órdenes de compra'),
    ('compras.orden_servicio',  'Órdenes de servicio'),
    ('compras.nota_compra',     'Notas de compra'),
    ('compras.factura_compra',  'Facturas de compra'),
    ('compras.liquidacion',     'Liquidaciones'),

    ('ventas.cliente',          'Clientes'),
    ('ventas.cotizacion',       'Cotizaciones'),
    ('ventas.nota_preventa',    'Notas de preventa'),
    ('ventas.comprobante',      'Comprobantes'),
    ('ventas.nota_credito',     'Notas de crédito'),
    ('ventas.nota_debito',      'Notas de débito'),
    ('ventas.resumen_diario',   'Resúmenes diarios'),

    ('configuracion.empresa',      'Empresa'),
    ('configuracion.sucursal',     'Establecimientos'),
    ('configuracion.usuario',      'Usuarios'),
    ('configuracion.rol',          'Roles y permisos'),
    ('configuracion.serie',        'Series y correlativos'),
    ('configuracion.comprobante',  'Tipos de comprobante'),
    ('configuracion.auditoria',    'Bitácora')
) AS d(codigo, nombre);


-- Ningun submodulo puede quedar sin su fila: si alguno de los de arriba se
-- escribio mal, o si una migracion futura anade funciones sin su submodulo, la
-- autorizacion los denegaria en silencio —falta un eslabon de la cadena— y
-- nadie sabria por que. Esto lo convierte en un despliegue que se detiene.
DO $$
DECLARE
    huerfanos text;
BEGIN
    SELECT string_agg(DISTINCT f.modulo, ', ')
    INTO huerfanos
    FROM permiso f
    WHERE f.nivel = 'FUNCION'
      AND NOT EXISTS (
            SELECT 1 FROM permiso s
            WHERE s.nivel = 'SUBMODULO' AND s.modulo = f.modulo);

    IF huerfanos IS NOT NULL THEN
        RAISE EXCEPTION 'Hay funciones sin su fila de SUBMODULO: %', huerfanos;
    END IF;
END $$;


-- -----------------------------------------------------------------------------
-- 4. Se concede a los roles lo que ya tenian de hecho
-- -----------------------------------------------------------------------------
--
-- Sin esto, la migracion dejaria a TODOS los roles sin poder nada: tendrian sus
-- funciones y les faltarian los dos eslabones de arriba. Ningun rol cambia de
-- permisos efectivos al aplicar esta migracion, que es la unica forma aceptable
-- de cambiar el modelo de autorizacion de un sistema en marcha.
--
-- Alcanza tambien a los roles a medida que las cuentas hayan creado, porque se
-- deriva de lo que cada rol tiene y no de una lista de codigos.

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT DISTINCT rp.rol_id, sub.id
FROM rol_permiso rp
    JOIN permiso f   ON f.id = rp.permiso_id AND f.nivel = 'FUNCION'
    JOIN permiso sub ON sub.nivel = 'SUBMODULO' AND sub.modulo = f.modulo
ON CONFLICT DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT DISTINCT rp.rol_id, m.id
FROM rol_permiso rp
    JOIN permiso f ON f.id = rp.permiso_id AND f.nivel = 'FUNCION'
    JOIN permiso m ON m.nivel = 'MODULO' AND m.modulo = split_part(f.modulo, '.', 1)
ON CONFLICT DO NOTHING;
