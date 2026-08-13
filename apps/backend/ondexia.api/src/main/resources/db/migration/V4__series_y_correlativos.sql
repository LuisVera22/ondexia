-- =============================================================================
-- Series y correlativos.
--
-- La pieza delicada del modulo. SUNAT no tolera correlativos duplicados y
-- penaliza los saltos, asi que esta tabla es la FUENTE UNICA del numero de cada
-- comprobante (DTE §5, F-02).
--
-- Sobre por que ultimo_numero es una columna y no una SEQUENCE de PostgreSQL:
-- una secuencia NO es transaccional. Entrega el numero fuera de la transaccion
-- y no lo devuelve si esta se deshace, de modo que cualquier rollback —una
-- validacion que falla despues de pedir el numero, un error de red— deja un
-- hueco permanente en la numeracion. Una columna con SELECT ... FOR UPDATE si
-- participa de la transaccion: si algo se deshace, el numero vuelve a estar
-- disponible.
--
-- El precio es que los concurrentes se serializan en esa fila. Es exactamente
-- lo que se quiere: emitir dos comprobantes con el mismo numero es peor que
-- emitir el segundo unos milisegundos mas tarde.
-- =============================================================================

CREATE TABLE serie_correlativo (
    id              uuid         PRIMARY KEY,
    empresa_id      uuid         NOT NULL REFERENCES empresa(id),

    -- El establecimiento que emite. SUNAT relaciona la serie con el anexo, y
    -- por eso no es opcional como en almacen.
    sucursal_id     uuid         NOT NULL REFERENCES sucursal(id),

    -- Catalogo 01 de SUNAT: 01 factura, 03 boleta, 07 nota de credito,
    -- 08 nota de debito, 09 guia de remision.
    tipo_documento  varchar(2)   NOT NULL,

    -- Cuatro caracteres. La primera la fija SUNAT segun el tipo: F para
    -- factura, B para boleta, T para guia.
    serie           varchar(4)   NOT NULL,

    /*
     * Ultimo numero EMITIDO, no el siguiente.
     *
     * Empieza en 0 y el primer comprobante es el 1. Guardar «el siguiente»
     * obligaria a decidir si una serie recien creada vale 1 —y entonces el 0 no
     * existe nunca— o 0, y la respuesta cambia segun quien lo lea.
     */
    ultimo_numero   bigint       NOT NULL DEFAULT 0,

    activa          boolean      NOT NULL DEFAULT true,
    creado_en       timestamptz  NOT NULL DEFAULT now(),
    actualizado_en  timestamptz  NOT NULL DEFAULT now(),

    -- La restriccion que sostiene todo: una serie es unica por empresa y tipo.
    CONSTRAINT serie_unica UNIQUE (empresa_id, tipo_documento, serie),

    -- El tipo, contra el catalogo 01. Duplica el enumerado de Java por el mismo
    -- motivo que los CHECK de V1: la aplicacion no es el unico que escribe aqui,
    -- y un valor que Java no sabe leer se convierte en una excepcion al cargar
    -- la entidad, muy lejos de donde se escribio.
    CONSTRAINT serie_tipo_valido
        CHECK (tipo_documento IN ('01', '03', '07', '08', '09')),

    /*
     * La letra de la serie tiene que corresponder al tipo.
     *
     * No es cosmetico: una serie con la letra equivocada no la rechaza nadie
     * hasta el envio a SUNAT, y para entonces todos los comprobantes de esa
     * serie ya estan emitidos y numerados. Rehacerlos exige comunicacion de baja
     * uno por uno.
     *
     * Las notas admiten F o B porque heredan la letra del documento que
     * modifican: una nota sobre una boleta con serie F apuntaria a un documento
     * que no existe en el libro de boletas.
     */
    CONSTRAINT serie_letra_segun_tipo CHECK (
        CASE tipo_documento
            WHEN '01' THEN serie ~ '^F[A-Z0-9]{3}$'
            WHEN '03' THEN serie ~ '^B[A-Z0-9]{3}$'
            WHEN '07' THEN serie ~ '^[FB][A-Z0-9]{3}$'
            WHEN '08' THEN serie ~ '^[FB][A-Z0-9]{3}$'
            WHEN '09' THEN serie ~ '^T[A-Z0-9]{3}$'
        END
    ),

    -- El numero no retrocede jamas. Si alguna vez una operacion intentara
    -- bajarlo, esto lo detiene en la base en vez de dejar la numeracion
    -- reutilizando numeros ya emitidos.
    CONSTRAINT serie_numero_no_negativo CHECK (ultimo_numero >= 0),

    -- Ocho digitos es el tope de SUNAT para el correlativo.
    CONSTRAINT serie_numero_dentro_del_tope CHECK (ultimo_numero <= 99999999)
);

CREATE INDEX serie_por_empresa ON serie_correlativo (empresa_id, activa);

-- Para resolver «que series puede usar este establecimiento», que es la
-- consulta del momento de emitir.
CREATE INDEX serie_por_sucursal ON serie_correlativo (sucursal_id, tipo_documento);

SELECT activar_aislamiento_empresa('serie_correlativo');

COMMENT ON TABLE serie_correlativo IS
    'Fuente unica del correlativo de cada comprobante. El numero se asigna con '
    'SELECT ... FOR UPDATE dentro de la transaccion que crea el documento '
    '(DTE F-02), nunca con una SEQUENCE: una secuencia no es transaccional y '
    'deja huecos ante cualquier rollback.';

COMMENT ON COLUMN serie_correlativo.ultimo_numero IS
    'Ultimo numero EMITIDO. El primer comprobante de una serie nueva es el 1.';
