-- =============================================================================
-- V22 · La comunicacion de baja: decirle a SUNAT que unas facturas no existen
-- =============================================================================
--
-- Plan del primer producto, iteracion 6 (doc 13 §6). Es distinta de la nota de
-- credito en lo que afirma —el comprobante NO DEBIO EXISTIR, en vez de «la
-- operacion se corrigio»— y en como viaja: SUNAT la recibe, devuelve un TICKET,
-- y la respuesta se pide despues. De ahi el estado EN_PROCESO, que un
-- comprobante nunca tiene porque su envio es sincrono.
--
-- Privilegios: ondexia_app hereda CRUD de la V8.
-- =============================================================================

CREATE TABLE comunicacion_baja (
    id                    uuid          PRIMARY KEY,
    empresa_id            uuid          NOT NULL REFERENCES empresa(id),

    -- El dia en que se emitio lo que se da de baja. SUNAT exige que todos los
    -- comprobantes de una comunicacion sean del mismo.
    fecha_comprobantes    date          NOT NULL,
    -- El dia en que se genera la comunicacion, que va en su identificador.
    fecha_generacion      date          NOT NULL,
    -- El correlativo dentro de ese dia: RA-yyyyMMdd-N.
    numero_del_dia        integer       NOT NULL,

    estado                varchar(12)   NOT NULL,
    intentos              integer       NOT NULL DEFAULT 1,
    encolada_en           timestamptz,
    respondida_en         timestamptz,
    -- Lo que SUNAT devuelve al recibir el archivo; con el se consulta despues.
    ticket                varchar(50),
    codigo_sunat          varchar(10),
    descripcion_sunat     text,
    clave_xml             varchar(300),
    clave_cdr             varchar(300),
    solicitada_por        uuid          NOT NULL REFERENCES usuario(id),
    creado_en             timestamptz   NOT NULL DEFAULT now(),
    actualizado_en        timestamptz   NOT NULL DEFAULT now(),

    -- Dos comunicaciones del mismo dia con el mismo numero serian, para SUNAT,
    -- el mismo documento.
    CONSTRAINT comunicacion_baja_numero_unico UNIQUE (empresa_id, fecha_generacion, numero_del_dia),
    CONSTRAINT comunicacion_baja_estado_valido CHECK (
        estado IN ('EN_COLA', 'EN_PROCESO', 'ACEPTADO', 'RECHAZADO', 'ERROR_ENVIO', 'ANULADO')),
    -- En proceso significa exactamente «SUNAT dio un ticket».
    CONSTRAINT comunicacion_baja_ticket_coherente CHECK (estado <> 'EN_PROCESO' OR ticket IS NOT NULL),
    CONSTRAINT comunicacion_baja_intentos_positivos CHECK (intentos >= 1)
);

CREATE INDEX comunicacion_baja_en_curso ON comunicacion_baja (empresa_id, estado)
    WHERE estado IN ('EN_COLA', 'EN_PROCESO');
CREATE INDEX comunicacion_baja_recientes ON comunicacion_baja (empresa_id, creado_en DESC);

SELECT activar_aislamiento_empresa('comunicacion_baja');

COMMENT ON TABLE comunicacion_baja IS
    'Comunicacion de baja de facturas ante SUNAT (RA). Envio asincrono: SUNAT devuelve '
    'un ticket y la respuesta se consulta despues. Doc 13 §6.';


CREATE TABLE comunicacion_baja_item (
    id                uuid          PRIMARY KEY,
    empresa_id        uuid          NOT NULL REFERENCES empresa(id),
    comunicacion_id   uuid          NOT NULL REFERENCES comunicacion_baja(id),
    documento_id      uuid          NOT NULL REFERENCES documento_venta(id),
    -- Copiados del documento, como en comprobante_electronico: son lo que va en
    -- el XML y no deben cambiar aunque el documento cambie de estado.
    tipo_documento    varchar(2)    NOT NULL,
    serie             varchar(4)    NOT NULL,
    numero            bigint        NOT NULL,
    motivo            varchar(300)  NOT NULL,

    -- Un documento no se da de baja dos veces en la misma comunicacion. Que no
    -- este en otra lo comprueba el caso de uso, que es donde se puede decir por
    -- que con un mensaje util.
    CONSTRAINT comunicacion_baja_item_unico UNIQUE (comunicacion_id, documento_id),
    -- La comunicacion de baja es para facturas y sus notas. Una boleta se anula
    -- con nota de credito y una nota de venta no se declara.
    CONSTRAINT comunicacion_baja_item_tipo CHECK (tipo_documento IN ('01', '07', '08'))
);

CREATE INDEX comunicacion_baja_item_por_documento ON comunicacion_baja_item (documento_id);

SELECT activar_aislamiento_empresa('comunicacion_baja_item');


-- -----------------------------------------------------------------------------
-- Permisos: el submodulo ventas.comunicacion_baja
-- -----------------------------------------------------------------------------
--
-- No existia: la V2 tiene ventas.resumen_diario pero no este. Se crea con el
-- mismo idioma que la V17 y la V19, y con las mismas dos acciones que tendria
-- el resumen: consultar y enviar. Dar de baja una factura tiene efecto
-- tributario, asi que 'enviar' va solo al Administrador del sistema.

INSERT INTO permiso (id, codigo, modulo, accion, nivel, nombre, descripcion)
VALUES (gen_random_uuid(), 'ventas.comunicacion_baja:acceder', 'ventas.comunicacion_baja',
        'acceder', 'SUBMODULO', 'Comunicaciones de baja',
        'Facturas que se comunican a SUNAT como no emitidas');

INSERT INTO permiso (id, codigo, modulo, accion, nivel, nombre, descripcion)
SELECT gen_random_uuid(), 'ventas.comunicacion_baja:' || d.accion, 'ventas.comunicacion_baja',
       d.accion, 'FUNCION', d.nombre, NULL
FROM (VALUES
    ('consultar', 'Consultar'),
    ('enviar',    'Dar de baja una factura')
) AS d(accion, nombre);

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT rp.rol_id, nuevo.id
FROM rol_permiso rp
    JOIN permiso m     ON m.id = rp.permiso_id AND m.nivel = 'MODULO' AND m.modulo = 'ventas'
    JOIN permiso nuevo ON nuevo.codigo = 'ventas.comunicacion_baja:acceder'
ON CONFLICT DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r, permiso p
WHERE r.cuenta_id IS NULL AND r.codigo = 'ADMINISTRADOR'
  AND p.modulo = 'ventas.comunicacion_baja'
ON CONFLICT DO NOTHING;

UPDATE cuenta SET permisos_version = permisos_version + 1;
