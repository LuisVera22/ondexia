-- =============================================================================
-- Almacenes.
--
-- La primera tabla de negocio del sistema, y por eso la primera que llama a
-- activar_aislamiento_empresa(). Es deliberadamente diminuta: si el aislamiento
-- fallara aqui, el fallo solo puede estar en el aislamiento — no hay logica
-- propia donde esconderse.
--
-- Un almacen no es un establecimiento. El establecimiento es la direccion que
-- SUNAT conoce; el almacen es donde estan fisicamente las existencias, y en un
-- mismo local puede haber varios (mostrador, deposito, mercaderia en transito).
-- Por eso sucursal_id es opcional: hay almacenes que no cuelgan de ningun anexo.
-- =============================================================================

CREATE TABLE almacen (
    id              uuid         PRIMARY KEY,
    empresa_id      uuid         NOT NULL REFERENCES empresa(id),

    -- Opcional a proposito: ver la cabecera.
    sucursal_id     uuid         REFERENCES sucursal(id),

    codigo          varchar(20)  NOT NULL,
    nombre          varchar(200) NOT NULL,
    activo          boolean      NOT NULL DEFAULT true,
    creado_en       timestamptz  NOT NULL DEFAULT now(),
    actualizado_en  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT almacen_codigo_unico UNIQUE (empresa_id, codigo)
);

-- El listado normal filtra por empresa y por activo. Sin este indice, cada
-- desplegable de almacenes recorre la tabla entera.
CREATE INDEX almacen_por_empresa ON almacen (empresa_id, activo);

/*
 * Aislamiento multiempresa.
 *
 * Esta linea es el motivo de que esta entrega exista y vaya antes que series y
 * correlativos: valida la funcion de RLS sobre una tabla de negocio real. Toda
 * tabla transaccional que se anada despues debe llamarla igual.
 */
SELECT activar_aislamiento_empresa('almacen');

COMMENT ON TABLE almacen IS
    'Ubicacion fisica de las existencias. Distinto de sucursal, que es el anexo '
    'ante SUNAT: en un mismo establecimiento puede haber varios almacenes.';
