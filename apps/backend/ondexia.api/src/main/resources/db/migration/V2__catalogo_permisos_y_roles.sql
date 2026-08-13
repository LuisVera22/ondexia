-- =============================================================================
-- V2 — Catalogo de permisos y roles predefinidos.
--
-- El catalogo lo define el sistema, no el cliente: es la lista de lo que
-- Ondexia sabe hacer. Lo que cada cuenta compone son sus roles, eligiendo de
-- esta lista.
--
-- Los permisos se declaran por modulo con sus acciones, y se expanden con un
-- unnest. Enumerar a mano los ~150 pares seria una pared de literales donde un
-- error tipografico produce un permiso que nadie tiene y una pantalla que nadie
-- puede abrir, sin ningun error visible.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Catalogo de permisos
-- -----------------------------------------------------------------------------
--
-- Las acciones no son iguales en todos los modulos, y esa es justamente la
-- decision de diseno: 'anular' solo existe donde anular significa algo, y
-- 'aprobar' solo donde hay una aprobacion real. Un catalogo con las mismas
-- cinco acciones en todas partes seria mas corto de escribir y mentiria sobre
-- lo que el sistema hace.

INSERT INTO permiso (id, codigo, modulo, accion, descripcion)
SELECT
    gen_random_uuid(),
    d.modulo || ':' || a.accion,
    d.modulo,
    a.accion,
    NULL
FROM (VALUES
    -- Almacen ---------------------------------------------------------------
    ('almacen.producto',        ARRAY['consultar', 'registrar', 'editar', 'desactivar']),
    ('almacen.marca',           ARRAY['consultar', 'registrar', 'editar', 'desactivar']),
    ('almacen.modelo',          ARRAY['consultar', 'registrar', 'editar', 'desactivar']),
    ('almacen.presentacion',    ARRAY['consultar', 'registrar', 'editar', 'desactivar']),
    ('almacen.tipo_precio',     ARRAY['consultar', 'registrar', 'editar', 'desactivar']),
    ('almacen.precio',          ARRAY['consultar', 'registrar', 'editar']),
    ('almacen.almacen',         ARRAY['consultar', 'registrar', 'editar', 'desactivar']),
    -- El stock no se 'edita': se mueve. Un ajuste manual es un movimiento con
    -- su motivo, no una correccion silenciosa del saldo — si no, el kardex deja
    -- de cuadrar con la existencia y no hay forma de averiguar cuando dejo de
    -- hacerlo.
    ('almacen.stock',           ARRAY['consultar', 'ajustar', 'trasladar']),
    ('almacen.kardex',          ARRAY['consultar', 'exportar']),
    ('almacen.guia_ingreso',    ARRAY['consultar', 'registrar', 'anular']),
    ('almacen.guia_remision',   ARRAY['consultar', 'registrar', 'anular', 'emitir']),

    -- Compras ---------------------------------------------------------------
    ('compras.proveedor',       ARRAY['consultar', 'registrar', 'editar', 'desactivar']),
    ('compras.nota_pedido',     ARRAY['consultar', 'registrar', 'editar', 'anular']),
    -- 'aprobar' separado de 'registrar': quien pide no autoriza el gasto.
    ('compras.orden_compra',    ARRAY['consultar', 'registrar', 'editar', 'aprobar', 'anular']),
    ('compras.orden_servicio',  ARRAY['consultar', 'registrar', 'editar', 'aprobar', 'anular']),
    ('compras.nota_compra',     ARRAY['consultar', 'registrar', 'anular']),
    ('compras.factura_compra',  ARRAY['consultar', 'registrar', 'editar', 'anular']),
    ('compras.liquidacion',     ARRAY['consultar', 'registrar', 'anular', 'emitir']),

    -- Ventas ----------------------------------------------------------------
    ('ventas.cliente',          ARRAY['consultar', 'registrar', 'editar', 'desactivar']),
    ('ventas.cotizacion',       ARRAY['consultar', 'registrar', 'editar', 'anular']),
    ('ventas.nota_preventa',    ARRAY['consultar', 'registrar', 'editar', 'anular']),
    -- 'emitir' y 'anular' son permisos distintos, y es la separacion mas
    -- importante del catalogo: anular un comprobante ya emitido tiene efecto
    -- tributario, y quien atiende el mostrador casi nunca debe poder hacerlo.
    ('ventas.comprobante',      ARRAY['consultar', 'registrar', 'emitir', 'anular', 'exportar']),
    ('ventas.nota_credito',     ARRAY['consultar', 'registrar', 'emitir']),
    ('ventas.nota_debito',      ARRAY['consultar', 'registrar', 'emitir']),
    ('ventas.resumen_diario',   ARRAY['consultar', 'enviar']),

    -- Configuracion ---------------------------------------------------------
    ('configuracion.empresa',   ARRAY['consultar', 'editar']),
    ('configuracion.sucursal',  ARRAY['consultar', 'registrar', 'editar', 'desactivar']),
    ('configuracion.usuario',   ARRAY['consultar', 'registrar', 'editar', 'desactivar']),
    ('configuracion.rol',       ARRAY['consultar', 'registrar', 'editar', 'eliminar']),
    ('configuracion.serie',     ARRAY['consultar', 'registrar', 'editar']),
    -- La bitacora se consulta y se exporta. No se registra a mano ni se borra:
    -- lo primero la falsearia y lo segundo lo impide un disparador (V1).
    ('configuracion.auditoria', ARRAY['consultar', 'exportar'])
) AS d(modulo, acciones)
CROSS JOIN LATERAL unnest(d.acciones) AS a(accion);


-- -----------------------------------------------------------------------------
-- 2. Roles predefinidos
-- -----------------------------------------------------------------------------
--
-- cuenta_id NULL = rol del sistema: lo ven todas las cuentas y ninguna puede
-- modificarlo. Un cliente que quiera ajustarlo lo duplica, y la copia queda con
-- su cuenta_id.
--
-- Esa inmutabilidad es intencionada. Si cada cliente pudiera editar
-- «Vendedor», la palabra dejaria de significar lo mismo entre clientes y
-- cualquier consulta de soporte empezaria por averiguar que quiere decir aqui.

INSERT INTO rol (id, cuenta_id, codigo, nombre, descripcion) VALUES
    (gen_random_uuid(), NULL, 'ADMINISTRADOR', 'Administrador',
     'Acceso completo a los modulos de la empresa. No gobierna la suscripcion.'),
    (gen_random_uuid(), NULL, 'VENDEDOR', 'Vendedor',
     'Emite comprobantes y gestiona clientes. No anula ni toca el catalogo.'),
    (gen_random_uuid(), NULL, 'ALMACENERO', 'Almacenero',
     'Gestiona catalogo, existencias y movimientos. No vende ni compra.'),
    (gen_random_uuid(), NULL, 'CONTADOR', 'Contador',
     'Consulta y exporta todo. Registra facturas de compra. No emite ni anula.');


-- -----------------------------------------------------------------------------
-- 3. Asignacion de permisos a los roles
-- -----------------------------------------------------------------------------
--
-- Se asignan por patron sobre modulo y accion, no enumerando codigos. Cuando en
-- una migracion futura aparezca 'ventas.guia_remision', el rol Administrador lo
-- recibira solo porque encaja en el patron, mientras que Vendedor no — que es
-- exactamente el comportamiento que se quiere: lo nuevo se abre a quien puede
-- todo y permanece cerrado para los demas hasta que alguien decida lo
-- contrario.

-- Administrador: todo lo de la empresa.
--
-- Nota deliberada: esto NO incluye gobernar la suscripcion ni crear empresas.
-- Eso es cuenta_administrador, que no pasa por esta matriz porque existe antes
-- de que haya ninguna empresa contra la que evaluar (ver V1 §5).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r, permiso p
WHERE r.cuenta_id IS NULL AND r.codigo = 'ADMINISTRADOR';


-- Vendedor: opera el mostrador.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r, permiso p
WHERE r.cuenta_id IS NULL AND r.codigo = 'VENDEDOR'
  AND (
        -- Todo lo de clientes y documentos previos a la venta.
        p.modulo IN ('ventas.cliente', 'ventas.cotizacion', 'ventas.nota_preventa')
        -- Comprobantes: puede consultarlos, registrarlos y emitirlos. NO anular:
        -- anular tiene efecto tributario y es el permiso que separa a quien
        -- atiende de quien responde.
     OR (p.modulo IN ('ventas.comprobante', 'ventas.nota_credito', 'ventas.nota_debito')
         AND p.accion IN ('consultar', 'registrar', 'emitir'))
        -- Necesita ver el catalogo y la existencia para vender, sin poder
        -- tocarlos.
     OR (p.modulo IN ('almacen.producto', 'almacen.marca', 'almacen.modelo',
                      'almacen.presentacion', 'almacen.precio', 'almacen.stock')
         AND p.accion = 'consultar')
  );


-- Almacenero: gestiona el catalogo y las existencias.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r, permiso p
WHERE r.cuenta_id IS NULL AND r.codigo = 'ALMACENERO'
  AND (
        p.modulo LIKE 'almacen.%'
        -- Registra la entrada de mercaderia contra la orden de compra, sin poder
        -- crear ni aprobar la orden. La separacion entre quien compra y quien
        -- recibe es el control clasico contra el ingreso ficticio de mercaderia.
     OR (p.modulo IN ('compras.orden_compra', 'compras.proveedor') AND p.accion = 'consultar')
  )
  -- Emitir una guia de remision es un acto ante SUNAT, no una operacion de
  -- almacen. Se consulta y se registra; emitir se concede aparte.
  AND NOT (p.modulo = 'almacen.guia_remision' AND p.accion = 'emitir');


-- Contador: mira todo, registra compras, no emite ni anula nada.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r, permiso p
WHERE r.cuenta_id IS NULL AND r.codigo = 'CONTADOR'
  AND (
        p.accion IN ('consultar', 'exportar')
     OR (p.modulo = 'compras.factura_compra' AND p.accion IN ('registrar', 'editar'))
  );
