-- =============================================================================
-- V19 · Documentos de venta: nota de venta, boleta y factura desde el mostrador
-- =============================================================================
--
-- Plan del primer producto, iteracion 4 (doc 12 §3.2, §3.3 y §4.3). Un documento
-- de venta nace completo —lineas, totales, pagos, cliente, sesion de caja— y
-- despues solo cambia su estado. La nota de venta es un documento interno con
-- serie y correlativo propios, fuera del catalogo 01: no se declara a SUNAT.
--
-- Privilegios: ondexia_app hereda CRUD de la V8. El documento se protege por
-- disparador, no por privilegios.
-- =============================================================================

-- La nota de venta entra a las series con la letra N. Los CHECK de la V4 solo
-- admitian el catalogo 01; se rehacen con el nuevo tipo. tipo_comprobante_empresa
-- no cambia: la nota de venta no se habilita ni deshabilita, siempre se emite.
ALTER TABLE serie_correlativo DROP CONSTRAINT serie_tipo_valido;
ALTER TABLE serie_correlativo ADD CONSTRAINT serie_tipo_valido
    CHECK (tipo_documento IN ('01', '03', '07', '08', '09', 'NV'));

ALTER TABLE serie_correlativo DROP CONSTRAINT serie_letra_segun_tipo;
ALTER TABLE serie_correlativo ADD CONSTRAINT serie_letra_segun_tipo CHECK (
    CASE tipo_documento
        WHEN '01' THEN serie ~ '^F[A-Z0-9]{3}$'
        WHEN '03' THEN serie ~ '^B[A-Z0-9]{3}$'
        WHEN '07' THEN serie ~ '^[FB][A-Z0-9]{3}$'
        WHEN '08' THEN serie ~ '^[FB][A-Z0-9]{3}$'
        WHEN '09' THEN serie ~ '^T[A-Z0-9]{3}$'
        WHEN 'NV' THEN serie ~ '^N[A-Z0-9]{3}$'
    END
);

-- Si el mostrador puede vender con existencias insuficientes (doc 12 §3.5).
-- Por omision si, con aviso; un almacen formal lo apaga.
ALTER TABLE empresa ADD COLUMN permite_venta_sin_stock boolean NOT NULL DEFAULT true;


CREATE TABLE documento_venta (
    id                  uuid          PRIMARY KEY,
    empresa_id          uuid          NOT NULL REFERENCES empresa(id),
    sucursal_id         uuid          NOT NULL REFERENCES sucursal(id),
    -- Toda venta ocurre dentro de una sesion de caja (doc 12 §3.4).
    sesion_caja_id      uuid          NOT NULL REFERENCES sesion_caja(id),

    -- 01 factura, 03 boleta, NV nota de venta. Las notas de credito (07)
    -- llegaran en la iteracion 6 por esta misma tabla.
    tipo_documento      varchar(2)    NOT NULL,
    -- Redundante con el tipo a proposito: es la columna por la que se separa
    -- lo que se declara de lo que no, sin conocer el catalogo.
    fiscal              boolean       NOT NULL,
    serie               varchar(4)    NOT NULL,
    numero              bigint        NOT NULL,
    -- Nulo: adquirente sin documento (boleta hasta S/ 700, nota de venta).
    cliente_id          uuid          REFERENCES cliente(id),
    fecha_emision       date          NOT NULL,
    emitido_en          timestamptz   NOT NULL,
    emitido_por         uuid          NOT NULL REFERENCES usuario(id),
    moneda              varchar(3)    NOT NULL DEFAULT 'PEN',

    total_gravado       numeric(18,6) NOT NULL,
    total_exonerado     numeric(18,6) NOT NULL,
    total_inafecto      numeric(18,6) NOT NULL,
    total_descuento     numeric(18,6) NOT NULL,
    total_igv           numeric(18,6) NOT NULL,
    total               numeric(18,6) NOT NULL,

    observaciones       text,
    -- La nota de venta de la que salio una boleta o factura (canje, iteracion 6).
    documento_origen_id uuid          REFERENCES documento_venta(id),
    estado              varchar(12)   NOT NULL,
    creado_en           timestamptz   NOT NULL DEFAULT now(),
    actualizado_en      timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT documento_venta_numero_unico UNIQUE (empresa_id, tipo_documento, serie, numero),
    CONSTRAINT documento_venta_tipo_valido CHECK (tipo_documento IN ('01', '03', '07', 'NV')),
    CONSTRAINT documento_venta_fiscal_coherente CHECK (fiscal = (tipo_documento <> 'NV')),
    CONSTRAINT documento_venta_estado_valido CHECK (
        estado IN ('EMITIDO', 'PENDIENTE', 'CANJEADO', 'ANULADO')),
    CONSTRAINT documento_venta_total_no_negativo CHECK (total >= 0)
);

CREATE INDEX documento_venta_recientes ON documento_venta (empresa_id, emitido_en DESC);
CREATE INDEX documento_venta_por_sesion ON documento_venta (sesion_caja_id);
CREATE INDEX documento_venta_por_cliente ON documento_venta (cliente_id) WHERE cliente_id IS NOT NULL;

SELECT activar_aislamiento_empresa('documento_venta');

/*
 * Inmutable una vez emitido, salvo el estado. Lo demas —lineas, totales,
 * cliente, numero— es lo que el cliente tiene impreso y lo que SUNAT recibio o
 * recibira; corregirlo es emitir una nota de credito, no un UPDATE.
 */
CREATE OR REPLACE FUNCTION impedir_modificacion_documento_venta() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Un documento de venta no se borra; se anula'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF row(NEW.*) IS DISTINCT FROM row(OLD.*)
       AND (NEW.id, NEW.empresa_id, NEW.sucursal_id, NEW.sesion_caja_id, NEW.tipo_documento,
            NEW.fiscal, NEW.serie, NEW.numero, NEW.cliente_id, NEW.fecha_emision,
            NEW.emitido_en, NEW.emitido_por, NEW.moneda, NEW.total_gravado,
            NEW.total_exonerado, NEW.total_inafecto, NEW.total_descuento, NEW.total_igv,
            NEW.total, NEW.observaciones, NEW.documento_origen_id, NEW.creado_en)
        IS DISTINCT FROM
           (OLD.id, OLD.empresa_id, OLD.sucursal_id, OLD.sesion_caja_id, OLD.tipo_documento,
            OLD.fiscal, OLD.serie, OLD.numero, OLD.cliente_id, OLD.fecha_emision,
            OLD.emitido_en, OLD.emitido_por, OLD.moneda, OLD.total_gravado,
            OLD.total_exonerado, OLD.total_inafecto, OLD.total_descuento, OLD.total_igv,
            OLD.total, OLD.observaciones, OLD.documento_origen_id, OLD.creado_en) THEN
        RAISE EXCEPTION 'Un documento de venta emitido no se modifica; solo cambia su estado'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER documento_venta_inmutable
    BEFORE UPDATE OR DELETE ON documento_venta
    FOR EACH ROW
    EXECUTE FUNCTION impedir_modificacion_documento_venta();

COMMENT ON TABLE documento_venta IS
    'Nota de venta, boleta o factura emitida desde el punto de venta. Inmutable '
    'salvo el estado. La nota de venta (NV, fiscal = false) no se declara a SUNAT.';


CREATE TABLE documento_venta_detalle (
    id               uuid          PRIMARY KEY,
    empresa_id       uuid          NOT NULL REFERENCES empresa(id),
    documento_id     uuid          NOT NULL REFERENCES documento_venta(id),
    orden            integer       NOT NULL,
    producto_id      uuid          NOT NULL REFERENCES producto(id),
    -- Copiados del producto: puede cambiar de nombre y el comprobante no.
    codigo           varchar(30)   NOT NULL,
    descripcion      varchar(300)  NOT NULL,
    unidad_medida    varchar(5)    NOT NULL,
    cantidad         numeric(18,6) NOT NULL,
    -- Con IGV; es lo que ve el cliente en el mostrador.
    precio_unitario  numeric(18,6) NOT NULL,
    -- Sin IGV; es lo que va en el XML.
    valor_unitario   numeric(18,6) NOT NULL,
    descuento        numeric(18,6) NOT NULL DEFAULT 0,
    afectacion_igv   varchar(2)    NOT NULL,
    valor_venta      numeric(18,6) NOT NULL,
    igv              numeric(18,6) NOT NULL,
    total            numeric(18,6) NOT NULL,
    descarga_existencias boolean   NOT NULL,

    CONSTRAINT detalle_orden_unico UNIQUE (documento_id, orden),
    CONSTRAINT detalle_cantidad_positiva CHECK (cantidad > 0)
);

CREATE INDEX documento_venta_detalle_por_documento ON documento_venta_detalle (documento_id);
CREATE INDEX documento_venta_detalle_por_producto ON documento_venta_detalle (producto_id);

SELECT activar_aislamiento_empresa('documento_venta_detalle');


CREATE TABLE pago (
    id            uuid          PRIMARY KEY,
    empresa_id    uuid          NOT NULL REFERENCES empresa(id),
    documento_id  uuid          NOT NULL REFERENCES documento_venta(id),
    forma         varchar(20)   NOT NULL,
    monto         numeric(18,6) NOT NULL,
    referencia    varchar(100),

    CONSTRAINT pago_forma_valida CHECK (
        forma IN ('EFECTIVO', 'TARJETA', 'TRANSFERENCIA', 'BILLETERA_DIGITAL')),
    CONSTRAINT pago_monto_positivo CHECK (monto > 0)
);

CREATE INDEX pago_por_documento ON pago (documento_id);

SELECT activar_aislamiento_empresa('pago');

COMMENT ON TABLE pago IS
    'Cobros de un documento, por forma de pago. Varias filas: pago mixto. El '
    'arqueo de la sesion de caja los suma por forma.';


-- -----------------------------------------------------------------------------
-- Permisos: el submodulo ventas.nota_venta
-- -----------------------------------------------------------------------------
--
-- Mismo idioma que la V17 para caja: el submodulo, a todo rol que ya alcance el
-- modulo `ventas`; las funciones, solo al Administrador del sistema. Boleta y
-- factura siguen bajo ventas.comprobante (V2).

INSERT INTO permiso (id, codigo, modulo, accion, nivel, nombre, descripcion)
VALUES (gen_random_uuid(), 'ventas.nota_venta:acceder', 'ventas.nota_venta', 'acceder',
        'SUBMODULO', 'Notas de venta', 'Documento interno del mostrador, no se declara a SUNAT');

INSERT INTO permiso (id, codigo, modulo, accion, nivel, nombre, descripcion)
SELECT gen_random_uuid(), 'ventas.nota_venta:' || d.accion, 'ventas.nota_venta', d.accion,
       'FUNCION', d.nombre, NULL
FROM (VALUES
    ('consultar', 'Consultar'),
    ('registrar', 'Registrar'),
    ('canjear',   'Canjear por boleta o factura'),
    ('anular',    'Anular')
) AS d(accion, nombre);

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT rp.rol_id, nuevo.id
FROM rol_permiso rp
    JOIN permiso m     ON m.id = rp.permiso_id AND m.nivel = 'MODULO' AND m.modulo = 'ventas'
    JOIN permiso nuevo ON nuevo.codigo = 'ventas.nota_venta:acceder'
ON CONFLICT DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r, permiso p
WHERE r.cuenta_id IS NULL AND r.codigo = 'ADMINISTRADOR'
  AND p.modulo = 'ventas.nota_venta'
ON CONFLICT DO NOTHING;

UPDATE cuenta SET permisos_version = permisos_version + 1;
