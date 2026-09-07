-- =============================================================================
-- V17 · Cajas y sesiones de caja
-- =============================================================================
--
-- Plan del primer producto, iteracion 2 (doc 12 §3.4 y §4.2). Una caja es un
-- punto de cobro dentro de un establecimiento; una sesion es un turno: desde
-- que alguien la abre con un monto inicial hasta que la cierra declarando lo
-- que conto. La venta (iteracion 4) ocurrira siempre dentro de una sesion.
--
-- Privilegios: ondexia_app necesita CRUD en las dos tablas y lo hereda de los
-- privilegios por omision de la V8. No hay nada que REVOKE aqui.
-- =============================================================================

CREATE TABLE caja (
    id              uuid         PRIMARY KEY,
    empresa_id      uuid         NOT NULL REFERENCES empresa(id),
    -- Obligatorio, a diferencia del almacen: una caja sin local seria un cobro
    -- sin sitio.
    sucursal_id     uuid         NOT NULL REFERENCES sucursal(id),

    codigo          varchar(20)  NOT NULL,
    nombre          varchar(200) NOT NULL,
    activo          boolean      NOT NULL DEFAULT true,
    creado_en       timestamptz  NOT NULL DEFAULT now(),
    actualizado_en  timestamptz  NOT NULL DEFAULT now(),

    -- Por establecimiento y no por empresa: dos locales pueden tener su CAJA1.
    CONSTRAINT caja_codigo_unico UNIQUE (empresa_id, sucursal_id, codigo)
);

CREATE INDEX caja_por_sucursal ON caja (sucursal_id, activo);

SELECT activar_aislamiento_empresa('caja');

COMMENT ON TABLE caja IS
    'Punto de cobro dentro de un establecimiento. Varias por local. No decide la '
    'serie del comprobante (eso es del establecimiento); decide a que arqueo se '
    'atribuye cada cobro.';


CREATE TABLE sesion_caja (
    id              uuid          PRIMARY KEY,
    empresa_id      uuid          NOT NULL REFERENCES empresa(id),
    caja_id         uuid          NOT NULL REFERENCES caja(id),

    abierta_por     uuid          NOT NULL REFERENCES usuario(id),
    abierta_en      timestamptz   NOT NULL,
    -- NUMERIC(18,6) como todo importe (CLAUDE.md). Es lo que hay en el cajon
    -- antes de vender.
    monto_inicial   numeric(18,6) NOT NULL,

    cerrada_por     uuid          REFERENCES usuario(id),
    cerrada_en      timestamptz,
    estado          varchar(10)   NOT NULL,

    -- Por forma de pago, como {"EFECTIVO": "120.50", ...}. jsonb y no cuatro
    -- columnas: el catalogo de formas de pago es un enumerado del dominio y una
    -- quinta forma no deberia exigir una migracion del arqueo.
    declarado       jsonb         NOT NULL DEFAULT '{}'::jsonb,
    calculado       jsonb         NOT NULL DEFAULT '{}'::jsonb,

    creado_en       timestamptz   NOT NULL DEFAULT now(),
    actualizado_en  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT sesion_estado_valido CHECK (estado IN ('ABIERTA', 'CERRADA')),
    CONSTRAINT sesion_monto_inicial_no_negativo CHECK (monto_inicial >= 0),
    -- Cerrada lleva quien y cuando; abierta, ninguno de los dos.
    CONSTRAINT sesion_cierre_completo CHECK (
        (estado = 'ABIERTA' AND cerrada_por IS NULL AND cerrada_en IS NULL)
     OR (estado = 'CERRADA' AND cerrada_por IS NOT NULL AND cerrada_en IS NOT NULL)
    )
);

-- Una caja tiene a lo sumo una sesion abierta. Indice unico parcial y no una
-- comprobacion en el codigo: dos aperturas a la vez leerian «ninguna abierta»
-- y las dos pasarian.
CREATE UNIQUE INDEX sesion_caja_una_abierta ON sesion_caja (caja_id) WHERE estado = 'ABIERTA';

CREATE INDEX sesion_caja_por_caja ON sesion_caja (caja_id, abierta_en DESC);

SELECT activar_aislamiento_empresa('sesion_caja');

/*
 * Una sesion cerrada es inmutable, por disparador y no solo por codigo. El
 * arqueo tiene valor probatorio para el negocio —es lo que dice cuanto falto—
 * y un arqueo que se puede reescribir no dice nada. Lo unico que cambia en una
 * sesion es su paso de ABIERTA a CERRADA; despues, nada.
 */
CREATE OR REPLACE FUNCTION impedir_modificacion_sesion_cerrada() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.estado = 'CERRADA' THEN
        RAISE EXCEPTION 'Una sesion de caja cerrada no se modifica ni se borra'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER sesion_caja_cerrada_inmutable
    BEFORE UPDATE OR DELETE ON sesion_caja
    FOR EACH ROW
    EXECUTE FUNCTION impedir_modificacion_sesion_cerrada();

COMMENT ON TABLE sesion_caja IS
    'Turno de caja: apertura con monto inicial, cierre con arqueo por forma de pago. '
    'Cerrada es inmutable (disparador). A lo sumo una abierta por caja (indice parcial).';


-- -----------------------------------------------------------------------------
-- Permisos: el submodulo ventas.caja
-- -----------------------------------------------------------------------------
--
-- Mismo idioma que la V7 para identidad visual: el submodulo, a todo rol que ya
-- alcance el modulo `ventas` —incluidos los roles a medida de las cuentas—; las
-- funciones, solo al Administrador del sistema. Que un Cajero pueda abrir y
-- cerrar su caja lo decide cada cuenta en su pantalla de roles: es exactamente
-- el tipo de decision que el doc 12 §6.1 deja en manos del cliente.

INSERT INTO permiso (id, codigo, modulo, accion, nivel, nombre, descripcion)
VALUES (gen_random_uuid(), 'ventas.caja:acceder', 'ventas.caja', 'acceder', 'SUBMODULO',
        'Cajas', 'Puntos de cobro y sus turnos');

INSERT INTO permiso (id, codigo, modulo, accion, nivel, nombre, descripcion)
SELECT gen_random_uuid(), 'ventas.caja:' || d.accion, 'ventas.caja', d.accion, 'FUNCION',
       d.nombre, NULL
FROM (VALUES
    ('consultar',  'Consultar'),
    ('registrar',  'Registrar'),
    ('editar',     'Editar'),
    ('desactivar', 'Desactivar'),
    ('abrir',      'Abrir caja'),
    ('cerrar',     'Cerrar caja')
) AS d(accion, nombre);

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT rp.rol_id, nuevo.id
FROM rol_permiso rp
    JOIN permiso m     ON m.id = rp.permiso_id AND m.nivel = 'MODULO' AND m.modulo = 'ventas'
    JOIN permiso nuevo ON nuevo.codigo = 'ventas.caja:acceder'
ON CONFLICT DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r, permiso p
WHERE r.cuenta_id IS NULL AND r.codigo = 'ADMINISTRADOR'
  AND p.modulo = 'ventas.caja'
ON CONFLICT DO NOTHING;

-- El catalogo cambio: que ninguna cache de permisos siga sirviendo el anterior.
UPDATE cuenta SET permisos_version = permisos_version + 1;
