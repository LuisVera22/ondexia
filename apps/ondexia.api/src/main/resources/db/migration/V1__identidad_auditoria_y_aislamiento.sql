-- =============================================================================
-- V1 — Identidad, bitacora y el mecanismo de aislamiento multiempresa.
--
-- Este archivo define el esquema base y, sobre todo, deja instalada la
-- maquinaria de Row Level Security que toda tabla transaccional posterior va a
-- usar. Merece leerse entero una vez.
--
-- Convenciones (DTE §5 y doc 03 §2):
--   · Nombres en snake_case espanol.
--   · Identificadores UUID generados por la aplicacion, ordenados por tiempo.
--   · Marcas de tiempo con zona horaria. Sin zona, un despliegue en otra region
--     reinterpreta las fechas ya guardadas y no hay forma de detectarlo.
--   · Los importes seran NUMERIC(18,6), jamas float. Aqui todavia no hay
--     ninguno; la regla queda escrita porque la primera tabla que los tenga se
--     escribira copiando el estilo de esta.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Aislamiento multiempresa
-- -----------------------------------------------------------------------------
--
-- La aplicacion filtra por empresa_id en cada consulta. RLS existe porque esa
-- defensa depende de que el desarrollador se acuerde, y una consulta olvidada
-- no falla: devuelve datos de otro cliente, con forma correcta, en la respuesta
-- equivocada. RLS convierte ese olvido en cero filas.
--
-- La variable la fija GestorTransaccionesConAislamiento al abrir cada
-- transaccion, con set_config(..., true) — es decir, valida solo dentro de la
-- transaccion y revertida por el motor al terminar. Esa palabra 'true' es lo
-- que impide la fuga por reutilizacion de conexiones en Lambda.
--
-- nullif(..., '') es lo que hace que falle cerrado: sin contexto la variable
-- vale cadena vacia, nullif la convierte en NULL, y `empresa_id = NULL` no es
-- cierto para ninguna fila.

CREATE OR REPLACE FUNCTION empresa_actual() RETURNS uuid
    LANGUAGE sql STABLE
AS $$
    SELECT nullif(current_setting('ondexia.empresa_id', true), '')::uuid;
$$;

COMMENT ON FUNCTION empresa_actual() IS
    'Empresa sobre la que opera la transaccion en curso, o NULL si no se fijo. '
    'La fija la aplicacion con set_config(''ondexia.empresa_id'', ..., true).';


-- Activa el aislamiento sobre una tabla que tenga columna empresa_id.
--
-- Se hace funcion y no se copia el bloque en cada migracion por una razon
-- practica: si la politica se escribe a mano tabla por tabla, tarde o temprano
-- una queda con una variante sutilmente distinta —un IS NOT NULL de mas, un
-- WITH CHECK de menos— y esa tabla deja de estar protegida sin que nadie lo
-- note. Con una funcion, activar el aislamiento es una linea y siempre la
-- misma.
--
-- FORCE es imprescindible: sin el, el propietario de la tabla ignora sus
-- propias politicas, y la aplicacion se conecta como propietario. Sin FORCE,
-- RLS quedaria activo y sin ningun efecto — el peor estado posible, porque
-- parece protegido.
CREATE OR REPLACE FUNCTION activar_aislamiento_empresa(nombre_tabla text) RETURNS void
    LANGUAGE plpgsql
AS $funcion$
BEGIN
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', nombre_tabla);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', nombre_tabla);
    EXECUTE format($politica$
        CREATE POLICY aislamiento_empresa ON %I
            USING (empresa_id = empresa_actual())
            WITH CHECK (empresa_id = empresa_actual())
    $politica$, nombre_tabla);
END;
$funcion$;

COMMENT ON FUNCTION activar_aislamiento_empresa(text) IS
    'Aplica la politica de aislamiento estandar a una tabla con columna empresa_id. '
    'Toda tabla transaccional nueva debe llamarla en su migracion.';


-- -----------------------------------------------------------------------------
-- 2. Cuenta y suscripcion
-- -----------------------------------------------------------------------------

CREATE TABLE cuenta (
    id                  uuid         PRIMARY KEY,
    nombre              varchar(200) NOT NULL,
    plan                varchar(30)  NOT NULL,
    estado_suscripcion  varchar(30)  NOT NULL,

    -- Se incrementa al tocar cualquier rol de la cuenta. Es la clave que permite
    -- cachear permisos en memoria del contenedor de Lambda sin servirlos rancios.
    permisos_version    bigint       NOT NULL DEFAULT 1,

    creado_en           timestamptz  NOT NULL DEFAULT now(),
    actualizado_en      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT cuenta_plan_valido
        CHECK (plan IN ('ESENCIAL', 'PROFESIONAL', 'CORPORATIVO')),
    CONSTRAINT cuenta_estado_valido
        CHECK (estado_suscripcion IN ('EN_PRUEBA', 'ACTIVA', 'SUSPENDIDA', 'CANCELADA'))
);

-- Los CHECK duplican los enumerados de Java a proposito. La aplicacion no es el
-- unico que escribe en esta base: tambien lo haran migraciones, scripts de
-- soporte y algun dia una consola de administracion. Un valor que Java no sabe
-- leer se convierte en una excepcion al cargar la entidad, en un sitio muy
-- lejos de donde se escribio.


CREATE TABLE usuario (
    id              uuid         PRIMARY KEY,
    cuenta_id       uuid         NOT NULL REFERENCES cuenta(id),

    -- Nulo mientras la persona no complete su registro en Cognito. El
    -- administrador puede darla de alta antes; se vincula al primer acceso.
    cognito_sub     varchar(64)  UNIQUE,

    email           varchar(254) NOT NULL,
    nombre          varchar(150) NOT NULL,
    activo          boolean      NOT NULL DEFAULT true,
    creado_en       timestamptz  NOT NULL DEFAULT now(),
    actualizado_en  timestamptz  NOT NULL DEFAULT now()
);

-- Unico por cuenta y sin distinguir mayusculas: dos usuarios con el mismo
-- correo escrito distinto son la misma persona intentando entrar dos veces, y
-- el dia que haya que recuperar una contrasena no habria forma de saber cual.
CREATE UNIQUE INDEX usuario_email_por_cuenta ON usuario (cuenta_id, lower(email));
CREATE INDEX usuario_por_cuenta ON usuario (cuenta_id);


-- -----------------------------------------------------------------------------
-- 3. Empresa y sucursal
-- -----------------------------------------------------------------------------

CREATE TABLE empresa (
    id                      uuid         PRIMARY KEY,
    cuenta_id               uuid         NOT NULL REFERENCES cuenta(id),

    -- Unico global, no por cuenta. Si dos clientes registran el mismo RUC hay
    -- dos sistemas emitiendo contra el mismo contribuyente y los correlativos se
    -- pisan. El segundo registro debe fallar y escalar a soporte.
    ruc                     varchar(11)  NOT NULL UNIQUE,

    razon_social            varchar(300) NOT NULL,
    nombre_comercial        varchar(300),
    domicilio_fiscal        varchar(400) NOT NULL,
    ubigeo                  varchar(6),

    -- Referencia al secreto, jamas el certificado. Un .pfx en la base aparece en
    -- cada respaldo y en cada volcado de desarrollo.
    secret_arn_certificado  varchar(512),
    usuario_sol             varchar(100),

    modo_sunat              varchar(20)  NOT NULL DEFAULT 'BETA',
    activo                  boolean      NOT NULL DEFAULT true,
    creado_en               timestamptz  NOT NULL DEFAULT now(),
    actualizado_en          timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT empresa_ruc_formato CHECK (ruc ~ '^[0-9]{11}$'),
    CONSTRAINT empresa_ubigeo_formato CHECK (ubigeo IS NULL OR ubigeo ~ '^[0-9]{6}$'),
    CONSTRAINT empresa_modo_sunat_valido CHECK (modo_sunat IN ('BETA', 'PRODUCCION'))
);

CREATE INDEX empresa_por_cuenta ON empresa (cuenta_id);


CREATE TABLE sucursal (
    id              uuid         PRIMARY KEY,
    empresa_id      uuid         NOT NULL REFERENCES empresa(id),

    -- Codigo del establecimiento anexo ante SUNAT, no un numero interno.
    codigo          varchar(10)  NOT NULL,

    nombre          varchar(200) NOT NULL,
    direccion       varchar(400) NOT NULL,
    ubigeo          varchar(6),
    activo          boolean      NOT NULL DEFAULT true,
    creado_en       timestamptz  NOT NULL DEFAULT now(),
    actualizado_en  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT sucursal_codigo_unico UNIQUE (empresa_id, codigo),
    CONSTRAINT sucursal_ubigeo_formato CHECK (ubigeo IS NULL OR ubigeo ~ '^[0-9]{6}$')
);


-- -----------------------------------------------------------------------------
-- 4. Roles y permisos
-- -----------------------------------------------------------------------------

CREATE TABLE permiso (
    id              uuid         PRIMARY KEY,

    -- Forma canonica modulo:accion, por ejemplo almacen.producto:registrar.
    codigo          varchar(100) NOT NULL UNIQUE,

    -- Descompuesto ademas en sus dos partes, para poder consultar por modulo o
    -- por accion con indice en vez de con LIKE sobre el codigo.
    modulo          varchar(60)  NOT NULL,
    accion          varchar(40)  NOT NULL,

    descripcion     varchar(300),
    creado_en       timestamptz  NOT NULL DEFAULT now(),
    actualizado_en  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX permiso_por_modulo ON permiso (modulo, accion);


CREATE TABLE rol (
    id              uuid         PRIMARY KEY,

    -- NULL = rol predefinido del sistema, comun a todas las cuentas e inmutable.
    cuenta_id       uuid         REFERENCES cuenta(id),

    codigo          varchar(60)  NOT NULL,
    nombre          varchar(120) NOT NULL,
    descripcion     varchar(300),
    creado_en       timestamptz  NOT NULL DEFAULT now(),
    actualizado_en  timestamptz  NOT NULL DEFAULT now()
);

-- Dos indices y no uno: el codigo debe ser unico entre los roles del sistema, y
-- unico dentro de cada cuenta, pero una cuenta si puede tener un rol con el
-- mismo codigo que uno del sistema — es justamente lo que pasa al duplicar uno
-- predefinido para ajustarlo.
CREATE UNIQUE INDEX rol_codigo_sistema ON rol (codigo) WHERE cuenta_id IS NULL;
CREATE UNIQUE INDEX rol_codigo_por_cuenta ON rol (cuenta_id, codigo) WHERE cuenta_id IS NOT NULL;


CREATE TABLE rol_permiso (
    rol_id      uuid NOT NULL REFERENCES rol(id) ON DELETE CASCADE,
    permiso_id  uuid NOT NULL REFERENCES permiso(id) ON DELETE CASCADE,

    PRIMARY KEY (rol_id, permiso_id)
);

-- El borrado en cascada aplica al rol, no al permiso: eliminar un rol de una
-- cuenta debe llevarse sus asignaciones, y eliminar un permiso del catalogo
-- —cosa que solo pasa al retirar una funcionalidad— debe limpiarlo de todos los
-- roles. Ninguno de los dos casos justifica dejar filas huerfanas.

CREATE INDEX rol_permiso_por_permiso ON rol_permiso (permiso_id);


-- -----------------------------------------------------------------------------
-- 5. Asignaciones
-- -----------------------------------------------------------------------------

CREATE TABLE usuario_empresa (
    id              uuid        PRIMARY KEY,
    usuario_id      uuid        NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    empresa_id      uuid        NOT NULL REFERENCES empresa(id),
    rol_id          uuid        NOT NULL REFERENCES rol(id),

    -- NULL = todas las sucursales.
    sucursal_id     uuid        REFERENCES sucursal(id),

    creado_en       timestamptz NOT NULL DEFAULT now(),
    actualizado_en  timestamptz NOT NULL DEFAULT now(),

    -- NULLS NOT DISTINCT es la parte que importa. Por omision PostgreSQL
    -- considera que un NULL nunca es igual a otro NULL, asi que un UNIQUE
    -- corriente permitiria crear dos veces la asignacion «todas las sucursales»
    -- para el mismo usuario y empresa — y con dos filas, cual gana la resolucion
    -- del contexto seria cuestion de suerte.
    -- Requiere PostgreSQL 15 o superior. La alternativa clasica son dos indices
    -- parciales, que hacen lo mismo con mas letra.
    CONSTRAINT usuario_empresa_unica
        UNIQUE NULLS NOT DISTINCT (usuario_id, empresa_id, sucursal_id)
);

CREATE INDEX usuario_empresa_por_usuario ON usuario_empresa (usuario_id);
CREATE INDEX usuario_empresa_por_empresa ON usuario_empresa (empresa_id);


CREATE TABLE cuenta_administrador (
    id              uuid        PRIMARY KEY,
    cuenta_id       uuid        NOT NULL REFERENCES cuenta(id),
    usuario_id      uuid        NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    creado_en       timestamptz NOT NULL DEFAULT now(),
    actualizado_en  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT cuenta_administrador_unico UNIQUE (cuenta_id, usuario_id)
);


-- El invariante: una cuenta nunca puede quedarse sin administrador.
--
-- Se defiende aqui y no solo en el servicio porque en Java no se puede
-- defender. Dos administradores renunciando en transacciones paralelas pasarian
-- ambos la comprobacion —cada uno ve al otro todavia presente— y la cuenta
-- quedaria huerfana, sin nadie capaz de arreglarla desde dentro.
--
-- Dos detalles hacen que esto funcione de verdad:
--
--   · SELECT ... FOR UPDATE sobre la cuenta serializa las transacciones que
--     tocan sus administradores. Sin ese bloqueo el disparador tendria la misma
--     carrera que el codigo Java.
--   · CONSTRAINT TRIGGER DEFERRABLE INITIALLY DEFERRED comprueba al confirmar y
--     no en cada fila. Asi, cambiar de administrador borrando el viejo e
--     insertando el nuevo dentro de una transaccion funciona; con un disparador
--     inmediato fallaria en el borrado, dependiendo del orden en que se
--     escribieron las dos sentencias.
CREATE OR REPLACE FUNCTION exigir_administrador_de_cuenta() RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    cuenta_afectada uuid := OLD.cuenta_id;
BEGIN
    PERFORM 1 FROM cuenta WHERE id = cuenta_afectada FOR UPDATE;

    IF NOT EXISTS (SELECT 1 FROM cuenta_administrador WHERE cuenta_id = cuenta_afectada) THEN
        RAISE EXCEPTION 'La cuenta % quedaria sin ningun administrador', cuenta_afectada
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER cuenta_administrador_no_vacia
    AFTER DELETE OR UPDATE ON cuenta_administrador
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION exigir_administrador_de_cuenta();


-- -----------------------------------------------------------------------------
-- 6. Bitacora
-- -----------------------------------------------------------------------------

CREATE TABLE auditoria (
    id             uuid        PRIMARY KEY,

    -- Nulo solo en las acciones anteriores a toda empresa: crear la cuenta, dar
    -- de alta al primer administrador.
    empresa_id     uuid        REFERENCES empresa(id),

    usuario_id     uuid        REFERENCES usuario(id),
    entidad        varchar(80) NOT NULL,
    entidad_id     uuid,
    accion         varchar(40) NOT NULL,

    -- Documento completo, no un diff. El diff depende de la version del codigo
    -- que lo calculo, y dentro de cinco anos —el plazo de conservacion fiscal—
    -- ese codigo no existe. El JSON crudo se sigue leyendo.
    datos_antes    jsonb,
    datos_despues  jsonb,

    ip             varchar(45),
    creado_en      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX auditoria_por_entidad ON auditoria (empresa_id, entidad, entidad_id, creado_en DESC);
CREATE INDEX auditoria_por_usuario ON auditoria (usuario_id, creado_en DESC);


-- Solo se inserta. Nunca se modifica ni se borra.
--
-- Se implementa con disparador y no revocando privilegios porque la aplicacion
-- se conecta como propietario de las tablas, y un propietario puede volver a
-- concederse lo que se le revoco. El disparador no.
--
-- Una bitacora que se puede editar no prueba nada, y en un sistema que emite
-- documentos con valor tributario esa prueba es el motivo por el que la tabla
-- existe.
CREATE OR REPLACE FUNCTION impedir_modificacion_auditoria() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'La bitacora es de solo insercion: no se permite % sobre auditoria', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER auditoria_solo_insercion
    BEFORE UPDATE OR DELETE ON auditoria
    FOR EACH ROW
    EXECUTE FUNCTION impedir_modificacion_auditoria();


-- Aislamiento de la bitacora.
--
-- No usa activar_aislamiento_empresa() porque su empresa_id admite nulo y la
-- politica estandar no lo contempla. Aqui:
--
--   · Al leer, solo se ven las filas de la empresa activa. Las de ambito de
--     cuenta (empresa_id nulo) no las ve ningun inquilino; se consultan con
--     herramientas de back-office, que es donde corresponde.
--   · Al insertar se admite el nulo, porque hay acciones legitimas que ocurren
--     antes de que exista ninguna empresa.
ALTER TABLE auditoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON auditoria
    USING (empresa_id = empresa_actual())
    WITH CHECK (empresa_id IS NULL OR empresa_id = empresa_actual());
