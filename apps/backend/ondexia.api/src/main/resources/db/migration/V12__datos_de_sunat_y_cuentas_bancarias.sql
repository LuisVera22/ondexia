-- =============================================================================
-- Lo que SUNAT dice de la empresa, y las cuentas por las que le pagan.
--
-- Ver ondexia.docs/11-registro-de-empresa.md
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Lo que viene de SUNAT
-- -----------------------------------------------------------------------------
--
-- Estas columnas NO las escribe una persona: las escribe la consulta del padron
-- (DT-19). La aplicacion las trata como de solo lectura hacia el usuario y las
-- refresca volviendo a consultar.
--
-- «No editable» no es «congelado»: una razon social cambia legitimamente en
-- SUNAT, y sin forma de refrescarla un dato corregible se vuelve imposible de
-- corregir. Peor que dejarlo editable, porque el rechazo de SUNAT no tendria
-- salida desde la aplicacion.

ALTER TABLE empresa ADD COLUMN estado_contribuyente varchar(20);
ALTER TABLE empresa ADD COLUMN condicion_domicilio  varchar(20);

-- Sin esta fecha, las dos de arriba mienten.
--
-- Son una foto del instante en que se consulto. Guardarlas a secas hace que la
-- base afirme «ACTIVO» sobre algo comprobado hace ocho meses, y esa afirmacion
-- se usaria para decidir si la empresa puede emitir. Con la fecha al lado, la
-- vista puede decir «ACTIVO, comprobado el 23/08», que es lo unico honesto.
ALTER TABLE empresa ADD COLUMN verificado_en timestamptz;

-- Desglose geografico. El ubigeo ya existia; estos tres son su parte legible.
--
-- Se guardan y no se derivan de un catalogo porque el catalogo de ubigeos no
-- existe todavia (ver el javadoc de Ubigeo) y porque son exactamente lo que
-- devuelve el proveedor.
ALTER TABLE empresa ADD COLUMN distrito     varchar(100);
ALTER TABLE empresa ADD COLUMN provincia    varchar(100);
ALTER TABLE empresa ADD COLUMN departamento varchar(100);

-- Los dos que cambian calculos, no solo la ficha.
--
-- Un agente de retencion aplica retencion en sus compras; un buen contribuyente
-- tiene otros plazos. Que los marque una persona a mano es invitar al error
-- justo donde el error tiene consecuencia fiscal. Por eso vienen de SUNAT y por
-- eso su valor por defecto es false y no NULL: son afirmaciones, y «no sabemos»
-- se representa con verificado_en nulo.
ALTER TABLE empresa ADD COLUMN es_agente_retencion   boolean NOT NULL DEFAULT false;
ALTER TABLE empresa ADD COLUMN es_buen_contribuyente boolean NOT NULL DEFAULT false;

-- Forma societaria: «SOCIEDAD ANONIMA CERRADA», «E.I.R.L.». Texto y no
-- enumerado: la lista de formas societarias del Peru es larga, la fija otro y
-- nosotros no decidimos nada con ella. Un enumerado obligaria a desplegar cada
-- vez que aparezca una que no previmos.
--
-- Persona natural o juridica NO esta aqui: se deriva de los dos primeros
-- digitos del RUC (Ruc.esPersonaJuridica). Un dato derivado guardado es un dato
-- que puede contradecir su origen.
ALTER TABLE empresa ADD COLUMN tipo_societario varchar(120);

-- Los valores replican los enumerados de Java a proposito, igual que en la V1:
-- la aplicacion no es la unica que escribe en esta base, y un valor que Java no
-- sabe leer se convierte en una excepcion al cargar la entidad, muy lejos de
-- donde se escribio.
--
-- La lista de estados es la de SUNAT, no una simplificacion nuestra: hay mas
-- situaciones que «activo o no».
ALTER TABLE empresa ADD CONSTRAINT empresa_estado_contribuyente_valido
    CHECK (estado_contribuyente IS NULL OR estado_contribuyente IN (
        'ACTIVO', 'BAJA_PROVISIONAL', 'BAJA_DEFINITIVA',
        'BAJA_PROVISIONAL_OFICIO', 'SUSPENSION_TEMPORAL', 'INSCRIPCION_OFICIO'));

ALTER TABLE empresa ADD CONSTRAINT empresa_condicion_domicilio_valido
    CHECK (condicion_domicilio IS NULL OR condicion_domicilio IN (
        'HABIDO', 'NO_HABIDO', 'NO_HALLADO', 'POR_VERIFICAR'));

-- Las tres van juntas o no van: un estado sin fecha de verificacion es la
-- mentira que verificado_en existe para evitar, y una fecha sin estado no dice
-- nada. La base lo impide en vez de confiar en que el codigo lo recuerde.
ALTER TABLE empresa ADD CONSTRAINT empresa_verificacion_completa
    CHECK ((estado_contribuyente IS NULL AND condicion_domicilio IS NULL
            AND verificado_en IS NULL)
        OR (estado_contribuyente IS NOT NULL AND condicion_domicilio IS NOT NULL
            AND verificado_en IS NOT NULL));

COMMENT ON COLUMN empresa.verificado_en IS
    'Cuando se comprobaron estado y condicion contra SUNAT. NULL significa '
    'nunca; no significa que esten bien.';
COMMENT ON COLUMN empresa.tipo_societario IS
    'Forma societaria segun SUNAT. Natural o juridica se deriva del RUC.';


-- -----------------------------------------------------------------------------
-- 2. La cuenta de detracciones
-- -----------------------------------------------------------------------------
--
-- Columna de empresa y NO fila de cuenta_bancaria, aunque sea una cuenta.
--
-- No es «una de las cuentas de la empresa», es LA cuenta: una sola, siempre del
-- Banco de la Nacion, siempre en soles, y no la elige la empresa. Como fila,
-- tres de sus columnas serian constantes y —lo que de verdad importa— cada
-- consulta que liste cuentas para cobrar tendria que acordarse de excluirla. Ese
-- filtro se olvida una vez y el resultado es una cuenta de detracciones
-- ofrecida como destino de pago en una factura. Como columna aparte no puede
-- pasar: no esta en la lista de la que se elige.
--
-- Sin longitud fija: no se pudo confirmar el formato del numero del BN ni en la
-- web del banco ni en la orientacion de SUNAT. Se comprueba que sean digitos y
-- nada mas. Un largo inventado rechazaria cuentas validas, que es peor que no
-- comprobar.
ALTER TABLE empresa ADD COLUMN cuenta_detracciones varchar(30);

ALTER TABLE empresa ADD CONSTRAINT empresa_cuenta_detracciones_formato
    CHECK (cuenta_detracciones IS NULL OR cuenta_detracciones ~ '^[0-9]+$');

COMMENT ON COLUMN empresa.cuenta_detracciones IS
    'Cuenta de detracciones en el Banco de la Nacion (SPOT). Sin longitud fija: '
    'el formato del BN no esta publicado. Aun sin uso; ver doc 11 §6.1.';


-- -----------------------------------------------------------------------------
-- 3. Cuentas bancarias
-- -----------------------------------------------------------------------------
--
-- Tabla y no columnas de empresa porque una empresa tiene varias, y en cuanto
-- hay dos ya no caben.
--
-- Sin columna `titular`: siempre es la razon social. Un campo que solo puede
-- tener un valor es un campo donde alguien escribira otro.

CREATE TABLE cuenta_bancaria (
    id              uuid         PRIMARY KEY,
    empresa_id      uuid         NOT NULL REFERENCES empresa(id),

    -- Codigo corto del banco, no su nombre. El nombre comercial cambia (BBVA
    -- Continental, Scotiabank) y lo que va escrito en comprobantes ya emitidos
    -- no deberia cambiar con el.
    banco           varchar(20)  NOT NULL,

    tipo            varchar(20)  NOT NULL,
    moneda          varchar(3)   NOT NULL,

    numero          varchar(30)  NOT NULL,

    -- Codigo de Cuenta Interbancario: 20 digitos, y este si tiene longitud
    -- publicada. Es el que un cliente necesita para transferir desde otro banco,
    -- asi que es el que suele aparecer en la factura.
    cci             varchar(20),

    alias           varchar(60),

    -- La que sale por defecto en el comprobante. La unicidad se garantiza abajo.
    principal       boolean      NOT NULL DEFAULT false,

    -- Nunca se borra una cuenta: puede estar impresa en comprobantes ya
    -- emitidos, y esos se conservan cinco anos. Se desactiva.
    activa          boolean      NOT NULL DEFAULT true,

    creado_en       timestamptz  NOT NULL DEFAULT now(),
    actualizado_en  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT cuenta_bancaria_tipo_valido
        CHECK (tipo IN ('CORRIENTE', 'AHORROS')),
    CONSTRAINT cuenta_bancaria_moneda_valida
        CHECK (moneda IN ('PEN', 'USD')),
    CONSTRAINT cuenta_bancaria_numero_formato
        CHECK (numero ~ '^[0-9-]+$'),
    CONSTRAINT cuenta_bancaria_cci_formato
        CHECK (cci IS NULL OR cci ~ '^[0-9]{20}$'),

    -- La misma cuenta no se registra dos veces en la misma empresa.
    CONSTRAINT cuenta_bancaria_numero_unico UNIQUE (empresa_id, banco, numero)
);

-- Una sola principal por empresa, y la impone la base.
--
-- Es un indice unico parcial y no un CHECK porque la regla cruza filas: «como
-- maximo una fila con principal = true por empresa». Dejarlo al codigo significa
-- que dos peticiones simultaneas dejan dos principales, y entonces cual sale en
-- el comprobante depende del orden de un ORDER BY que nadie escribio.
CREATE UNIQUE INDEX cuenta_bancaria_una_principal
    ON cuenta_bancaria (empresa_id) WHERE principal;

-- El desplegable de cuentas filtra por empresa y por activa.
CREATE INDEX cuenta_bancaria_por_empresa ON cuenta_bancaria (empresa_id, activa);

SELECT activar_aislamiento_empresa('cuenta_bancaria');

COMMENT ON TABLE cuenta_bancaria IS
    'Cuentas de cobro de la empresa. La de detracciones NO esta aqui: es una '
    'columna de empresa, para que no aparezca como destino de pago.';


-- -----------------------------------------------------------------------------
-- 4. Permisos
-- -----------------------------------------------------------------------------
--
-- La V8 concede a ondexia_app privilegios sobre las tablas que existian
-- entonces. Una tabla nueva no hereda nada, asi que sin este bloque la
-- aplicacion no podria leerla — y el fallo aparece en tiempo de ejecucion, no
-- al migrar.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ondexia_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON cuenta_bancaria TO ondexia_app;
    END IF;
END
$$;

-- Al panel interno NO se le concede nada sobre esta tabla, y conviene decir por
-- que: no es una omision.
--
-- La tabla lleva aislamiento por empresa, y la politica de RLS se aplica tambien
-- a ondexia_panel. Un GRANT SELECT le devolveria cero filas siempre —el panel no
-- fija ondexia.empresa_id— y eso es lo peor de los dos mundos: parece que tiene
-- acceso y en realidad no lee nada. Si algun dia el soporte necesita ver las
-- cuentas de un cliente, hara falta una decision explicita sobre como cruza esa
-- frontera, no un permiso suelto aqui.

