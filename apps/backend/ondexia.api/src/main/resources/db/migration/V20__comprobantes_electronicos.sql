-- =============================================================================
-- V20 · Emisión electrónica: el comprobante ante SUNAT y las credenciales
-- =============================================================================
--
-- Plan del primer producto, iteracion 5 (doc 12 §5 y doc 14). Una boleta o
-- factura tiene exactamente un comprobante electronico, que nace EN_COLA al
-- emitirse el documento y va cambiando con lo que el Emisor traiga de vuelta.
--
-- Privilegios: ondexia_app hereda CRUD de la V8. Aqui no hace falta disparador
-- de inmutabilidad: la fila cambia de estado con cada intento y guarda el
-- ultimo resultado; el historial detallado esta en los objetos del bus.
-- =============================================================================

-- ── Empresa: del ARN de Secrets Manager al certificado en el bucket ──────────
--
-- El DTE §8.2 decia Secrets Manager y la V1 dejo la columna. El doc 12 §5.3
-- lo cambio: el .pfx va a un bucket privado y su contrasena, con la clave SOL,
-- a un objeto que la API puede escribir y no leer. Aqui queda solo lo que la
-- pantalla necesita saber del certificado. La columna vieja nunca tuvo un
-- valor en ningun entorno; se retira en vez de dejarla como fosil.
ALTER TABLE empresa DROP COLUMN secret_arn_certificado;

ALTER TABLE empresa
    ADD COLUMN certificado_cargado_en    timestamptz,
    ADD COLUMN certificado_verificado_en timestamptz,
    ADD COLUMN certificado_sujeto        varchar(300),
    ADD COLUMN certificado_vence_en      date,
    ADD COLUMN certificado_error         text;

-- Sin certificado cargado no hay nada que verificar: lo demas depende de la
-- fecha de carga, igual que la verificacion de SUNAT depende de verificado_en.
ALTER TABLE empresa ADD CONSTRAINT empresa_certificado_coherente CHECK (
    certificado_cargado_en IS NOT NULL
    OR (certificado_verificado_en IS NULL AND certificado_sujeto IS NULL
        AND certificado_vence_en IS NULL AND certificado_error IS NULL)
);

COMMENT ON COLUMN empresa.certificado_cargado_en IS
    'Cuando la pantalla confirmo que el .pfx quedo en certificados/<ruc>.pfx del bucket '
    'de emision. El archivo y su contrasena no estan en la base (doc 14 §4).';


-- ── El comprobante ante SUNAT ────────────────────────────────────────────────

CREATE TABLE comprobante_electronico (
    id               uuid          PRIMARY KEY,
    empresa_id       uuid          NOT NULL REFERENCES empresa(id),
    documento_id     uuid          NOT NULL REFERENCES documento_venta(id),
    -- Copiados del documento para que el listado y el nombre del archivo no
    -- necesiten un JOIN. No cambian: el documento es inmutable.
    tipo_documento   varchar(2)    NOT NULL,
    serie            varchar(4)    NOT NULL,
    numero           bigint        NOT NULL,

    estado           varchar(12)   NOT NULL,
    intentos         integer       NOT NULL DEFAULT 1,
    encolado_en      timestamptz,
    respondido_en    timestamptz,
    -- Lo ultimo que dijo SUNAT (o el Emisor, en un fallo local).
    codigo_sunat       varchar(10),
    descripcion_sunat  text,
    -- Notas del CDR (codigos 4000+): aceptado con reparos.
    observaciones    jsonb         NOT NULL DEFAULT '[]'::jsonb,
    -- Objetos del bus. El XML se conserva aunque SUNAT rechace.
    clave_xml        varchar(300),
    clave_cdr        varchar(300),
    -- El DigestValue de la firma, que va impreso y en el QR.
    resumen_firma    varchar(100),
    creado_en        timestamptz   NOT NULL DEFAULT now(),
    actualizado_en   timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT comprobante_electronico_documento_unico UNIQUE (documento_id),
    CONSTRAINT comprobante_electronico_tipo_fiscal CHECK (tipo_documento IN ('01', '03', '07', '08')),
    CONSTRAINT comprobante_electronico_estado_valido CHECK (
        estado IN ('EN_COLA', 'ACEPTADO', 'RECHAZADO', 'ERROR_ENVIO', 'ANULADO')),
    CONSTRAINT comprobante_electronico_intentos_positivos CHECK (intentos >= 1)
);

CREATE INDEX comprobante_electronico_por_estado ON comprobante_electronico (empresa_id, estado);

SELECT activar_aislamiento_empresa('comprobante_electronico');

COMMENT ON TABLE comprobante_electronico IS
    'La vida de una boleta o factura ante SUNAT: en cola, aceptado, rechazado o con '
    'error de envio. Uno por documento fiscal. El XML y el CDR viven en el bucket del bus.';
