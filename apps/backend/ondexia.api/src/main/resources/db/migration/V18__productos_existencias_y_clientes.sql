-- =============================================================================
-- V18 · Productos, disponibilidad por local, existencias y clientes
-- =============================================================================
--
-- Plan del primer producto, iteracion 3 (doc 12 §3.5, §4.1 y §4.3). El catalogo
-- es de la empresa; lo que es del local es si el producto se vende ahi, a que
-- precio y cuanto hay. Las existencias se llevan como libro (movimiento_stock,
-- solo insercion) mas su proyeccion (stock), desde el primer dia.
--
-- Privilegios: ondexia_app hereda CRUD de los privilegios por omision de la V8
-- sobre las cinco tablas; no hay nada que REVOKE. El libro se protege por
-- disparador, no por privilegios: quien es dueno de la tabla siempre puede
-- volver a concederse lo que se le quite.
-- =============================================================================

-- Busqueda por nombre con trigramas: «cemen» encuentra «Cemento» y «CEMENTO».
-- Extension de confianza en PostgreSQL 13+: la crea el dueno de la base sin ser
-- superusuario, que es lo que ondexia_migraciones es en RDS.
CREATE EXTENSION IF NOT EXISTS pg_trgm;


CREATE TABLE producto (
    id              uuid          PRIMARY KEY,
    empresa_id      uuid          NOT NULL REFERENCES empresa(id),

    codigo          varchar(30)   NOT NULL,
    nombre          varchar(300)  NOT NULL,
    descripcion     text,
    -- Catalogo 03 de SUNAT (NIU, KGM, ZZ...). El dominio lo acota a una lista.
    unidad_medida   varchar(5)    NOT NULL,
    -- Catalogo 07: 10 gravado, 20 exonerado, 30 inafecto. Las variantes
    -- gratuitas son de la operacion, no del bien.
    afectacion_igv  varchar(2)    NOT NULL,
    precio_lista    numeric(18,6) NOT NULL DEFAULT 0,
    -- Un servicio no tiene existencias y venderlo no descarga nada.
    controla_stock  boolean       NOT NULL DEFAULT true,
    activo          boolean       NOT NULL DEFAULT true,
    creado_en       timestamptz   NOT NULL DEFAULT now(),
    actualizado_en  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT producto_codigo_unico UNIQUE (empresa_id, codigo),
    CONSTRAINT producto_afectacion_valida CHECK (afectacion_igv IN ('10', '20', '30')),
    CONSTRAINT producto_precio_no_negativo CHECK (precio_lista >= 0)
);

CREATE INDEX producto_por_empresa ON producto (empresa_id, activo);
CREATE INDEX producto_por_nombre ON producto USING gin (nombre gin_trgm_ops);

SELECT activar_aislamiento_empresa('producto');

COMMENT ON TABLE producto IS
    'Catalogo de bienes y servicios de la empresa. Lo que define al bien ante SUNAT '
    '(unidad, afectacion) y lo que comparten todos los locales (precio de lista).';


CREATE TABLE producto_local (
    id              uuid          PRIMARY KEY,
    empresa_id      uuid          NOT NULL REFERENCES empresa(id),
    producto_id     uuid          NOT NULL REFERENCES producto(id),
    sucursal_id     uuid          NOT NULL REFERENCES sucursal(id),

    disponible      boolean       NOT NULL DEFAULT true,
    -- NULL: rige el precio de lista.
    precio          numeric(18,6),
    creado_en       timestamptz   NOT NULL DEFAULT now(),
    actualizado_en  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT producto_local_unico UNIQUE (producto_id, sucursal_id),
    CONSTRAINT producto_local_precio_no_negativo CHECK (precio IS NULL OR precio >= 0)
);

CREATE INDEX producto_local_por_sucursal ON producto_local (sucursal_id, disponible);

SELECT activar_aislamiento_empresa('producto_local');

COMMENT ON TABLE producto_local IS
    'Si un producto se vende en un establecimiento y a que precio. Sin fila, no se '
    'vende ahi: un producto nuevo se ofrece solo en el local donde se creo.';


CREATE TABLE movimiento_stock (
    id              uuid          PRIMARY KEY,
    empresa_id      uuid          NOT NULL REFERENCES empresa(id),
    almacen_id      uuid          NOT NULL REFERENCES almacen(id),
    producto_id     uuid          NOT NULL REFERENCES producto(id),

    -- Con signo: positiva entra, negativa sale.
    cantidad        numeric(18,6) NOT NULL,
    tipo            varchar(20)   NOT NULL,
    -- Lo que lo origino, cuando fue un documento. Un ajuste no tiene.
    documento_tipo  varchar(10),
    documento_id    uuid,
    motivo          text,
    usuario_id      uuid          REFERENCES usuario(id),
    creado_en       timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT movimiento_con_cantidad CHECK (cantidad <> 0),
    CONSTRAINT movimiento_tipo_valido CHECK (
        tipo IN ('AJUSTE', 'INGRESO', 'VENTA', 'DEVOLUCION', 'TRASLADO'))
);

CREATE INDEX movimiento_stock_por_producto ON movimiento_stock (producto_id, creado_en DESC);
CREATE INDEX movimiento_stock_por_documento ON movimiento_stock (documento_id)
    WHERE documento_id IS NOT NULL;

SELECT activar_aislamiento_empresa('movimiento_stock');

/*
 * El libro es de solo insercion, por disparador. Un error se compensa con otro
 * movimiento; reescribir el libro deja una proyeccion en la que nadie confia.
 */
CREATE OR REPLACE FUNCTION impedir_modificacion_movimiento_stock() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'El libro de existencias es de solo insercion: no se permite % sobre movimiento_stock', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER movimiento_stock_solo_insercion
    BEFORE UPDATE OR DELETE ON movimiento_stock
    FOR EACH ROW
    EXECUTE FUNCTION impedir_modificacion_movimiento_stock();

COMMENT ON TABLE movimiento_stock IS
    'Libro mayor de existencias: cada entrada y salida, con signo. Solo insercion.';


CREATE TABLE stock (
    id              uuid          PRIMARY KEY,
    empresa_id      uuid          NOT NULL REFERENCES empresa(id),
    almacen_id      uuid          NOT NULL REFERENCES almacen(id),
    producto_id     uuid          NOT NULL REFERENCES producto(id),
    cantidad        numeric(18,6) NOT NULL DEFAULT 0,
    actualizado_en  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT stock_unico UNIQUE (almacen_id, producto_id)
);

CREATE INDEX stock_por_producto ON stock (producto_id);

SELECT activar_aislamiento_empresa('stock');

COMMENT ON TABLE stock IS
    'Proyeccion de movimiento_stock: cuanto hay por almacen y producto. Se actualiza '
    'en la misma transaccion que el movimiento y nunca por su cuenta.';


CREATE TABLE cliente (
    id                uuid          PRIMARY KEY,
    empresa_id        uuid          NOT NULL REFERENCES empresa(id),

    -- Catalogo 06 de SUNAT: 1 DNI, 4 carne de extranjeria, 6 RUC, 7 pasaporte.
    -- Sin el 0: el cliente sin documento no es una fila, es la ausencia de
    -- cliente en la venta.
    tipo_documento    varchar(1)    NOT NULL,
    numero_documento  varchar(15)   NOT NULL,
    nombre            varchar(300)  NOT NULL,
    direccion         varchar(300),
    correo            varchar(200),
    telefono          varchar(30),
    -- Cuando se comprobo el RUC contra el padron. Nulo: nunca, o no es RUC.
    verificado_en     timestamptz,
    activo            boolean       NOT NULL DEFAULT true,
    creado_en         timestamptz   NOT NULL DEFAULT now(),
    actualizado_en    timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT cliente_documento_unico UNIQUE (empresa_id, tipo_documento, numero_documento),
    CONSTRAINT cliente_tipo_documento_valido CHECK (tipo_documento IN ('1', '4', '6', '7')),
    CONSTRAINT cliente_dni_formato CHECK (tipo_documento <> '1' OR numero_documento ~ '^[0-9]{8}$'),
    CONSTRAINT cliente_ruc_formato CHECK (tipo_documento <> '6' OR numero_documento ~ '^[0-9]{11}$')
);

CREATE INDEX cliente_por_empresa ON cliente (empresa_id, activo);
CREATE INDEX cliente_por_nombre ON cliente USING gin (nombre gin_trgm_ops);

SELECT activar_aislamiento_empresa('cliente');

COMMENT ON TABLE cliente IS
    'Adquirente identificado. El documento no cambia: identifica al cliente ante '
    'SUNAT y ya esta impreso en sus comprobantes.';
