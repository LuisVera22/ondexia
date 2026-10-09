-- =============================================================================
-- V5 — Que tipos de comprobante emite cada empresa.
--
-- Es la parte de la pantalla «Comprobantes» que entra en este modulo (plan 07
-- §1.1: parcial). El resto —emitir, anular, consultar el estado ante SUNAT— es
-- de C1 y no vive aqui.
--
-- LA TABLA GUARDA DECISIONES, NO ESTADO COMPLETO.
--
-- Una empresa sin ninguna fila tiene los cinco tipos habilitados. Solo aparece
-- fila cuando alguien decide algo distinto de lo predeterminado. Esa asimetria
-- resuelve un problema concreto y no es un atajo:
--
--   · RegistrarCuenta corre SIN contexto de empresa —es el caso de uso que crea
--     la empresa— asi que no puede insertar en una tabla con RLS. Es el mismo
--     motivo por el que el alta tampoco escribe en `auditoria`. Con «sin fila =
--     habilitado» no hay nada que sembrar y el alta no cambia.
--   · Las empresas que ya existen quedan habilitadas sin necesidad de un UPDATE
--     masivo en esta migracion, que ademas tendria que adivinar que emite cada
--     una.
--
-- El precio es que hay que leer la ausencia como un valor, y por eso esta
-- escrito aqui arriba y no en un comentario de una columna.
-- =============================================================================

CREATE TABLE tipo_comprobante_empresa (
    id              uuid         PRIMARY KEY,
    empresa_id      uuid         NOT NULL REFERENCES empresa(id),

    -- Catalogo 01 de SUNAT, igual que en serie_correlativo.
    tipo_documento  varchar(2)   NOT NULL,

    activo          boolean      NOT NULL,
    creado_en       timestamptz  NOT NULL DEFAULT now(),
    actualizado_en  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT tipo_comprobante_unico UNIQUE (empresa_id, tipo_documento),
    CONSTRAINT tipo_comprobante_valido
        CHECK (tipo_documento IN ('01', '03', '07', '08', '09'))
);

CREATE INDEX tipo_comprobante_por_empresa ON tipo_comprobante_empresa (empresa_id);

SELECT activar_aislamiento_empresa('tipo_comprobante_empresa');

COMMENT ON TABLE tipo_comprobante_empresa IS
    'Decisiones explicitas sobre que tipos emite la empresa. La AUSENCIA de fila '
    'significa habilitado: solo se guarda lo que se aparta de lo predeterminado.';


-- -----------------------------------------------------------------------------
-- El permiso que gobierna la pantalla
-- -----------------------------------------------------------------------------
--
-- Se anade al catalogo y se asigna a mano a los roles predefinidos. Los INSERT
-- por patron de la V2 ya corrieron: un permiso creado despues no lo recoge
-- nadie, y sin estas dos sentencias la pantalla existiria sin que ningun rol
-- pudiera abrirla.
--
-- Es exactamente el comportamiento que la V2 describia —lo nuevo permanece
-- cerrado hasta que alguien decide lo contrario— y aqui alguien lo decide.

INSERT INTO permiso (id, codigo, modulo, accion, descripcion)
SELECT gen_random_uuid(), 'configuracion.comprobante:' || a.accion,
       'configuracion.comprobante', a.accion, NULL
FROM unnest(ARRAY['consultar', 'editar']) AS a(accion);

-- Administrador: los dos.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r, permiso p
WHERE r.cuenta_id IS NULL AND r.codigo = 'ADMINISTRADOR'
  AND p.modulo = 'configuracion.comprobante';

-- Contador: consulta y exporta todo, no configura nada.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r, permiso p
WHERE r.cuenta_id IS NULL AND r.codigo = 'CONTADOR'
  AND p.modulo = 'configuracion.comprobante' AND p.accion = 'consultar';


-- Los roles a medida que las cuentas hayan duplicado de ADMINISTRADOR se
-- quedan sin el permiso nuevo, y es lo correcto: son copias tomadas en un
-- momento dado, no herencias vivas. Quien las use lo anadira desde la matriz.
