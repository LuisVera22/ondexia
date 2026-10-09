-- =============================================================================
-- V7 — Identidad visual: los logos de cada empresa.
--
-- La tabla guarda CLAVES DE S3, nunca los bytes. Un logo dentro de la base
-- aparece en cada respaldo y en cada volcado de desarrollo, y multiplica el
-- tamano de algo que se restaura entero cuando hay una urgencia.
--
-- Va en tabla propia y no en columnas de `empresa` por dos motivos. `empresa`
-- es el agregado de identidad fiscal —RUC, razon social, domicilio— y esto es
-- presentacion; y ademas asi la tabla entra bajo Row Level Security como
-- cualquier otra de negocio, mientras que `empresa` esta fuera por el problema
-- de arranque del contexto.
--
-- CADA SUBIDA ESTRENA CLAVE. No se sobrescribe la anterior.
--
-- Es lo que hace que un PDF emitido el ano pasado siga mostrando el logo que
-- tenia entonces: su clave sigue existiendo. Reemplazar el objeto reescribiria
-- la historia en silencio —saldria un documento que nunca existio asi— que es
-- justo lo que el versionado del bucket pretende evitar (DTE §5.8, categoria
-- D). Con clave nueva por subida, el versionado pasa a ser red de seguridad en
-- vez de mecanismo principal. De regalo, no hace falta invalidar CloudFront
-- nunca: una clave nueva es una URL nueva.
-- =============================================================================

CREATE TABLE identidad_visual (
    id              uuid         PRIMARY KEY,

    -- UNIQUE: una empresa tiene una identidad visual, no varias.
    empresa_id      uuid         NOT NULL UNIQUE REFERENCES empresa(id),

    -- Claves de S3 dentro del bucket de marca. Nulo = sin archivo cargado.
    logo_principal  varchar(400),
    logo_ticket     varchar(400),
    simbolo         varchar(400),

    creado_en       timestamptz  NOT NULL DEFAULT now(),
    actualizado_en  timestamptz  NOT NULL DEFAULT now()
);

SELECT activar_aislamiento_empresa('identidad_visual');

COMMENT ON TABLE identidad_visual IS
    'Claves de S3 de los logos de la empresa, nunca los bytes. Cada subida '
    'estrena clave para que los documentos ya emitidos conserven el logo con '
    'el que se compusieron.';


-- -----------------------------------------------------------------------------
-- El submodulo nuevo, concedido POR PATRON
-- -----------------------------------------------------------------------------
--
-- Esta es la parte que la V6 prometia y aqui se estrena. Un submodulo nuevo no
-- lo hereda nadie por tener su modulo: la autorizacion es conjuntiva y hacen
-- falta los tres eslabones. Lo que la jerarquia si permite es conceder por
-- PATRON —«a todo rol que alcance el modulo configuracion»— en vez de enumerar
-- roles uno por uno, que es lo que habria que hacer con permisos planos y lo
-- que se olvida con los roles a medida de cada cliente.

INSERT INTO permiso (id, codigo, modulo, accion, nivel, nombre, descripcion)
VALUES (gen_random_uuid(), 'configuracion.identidad:acceder',
        'configuracion.identidad', 'acceder', 'SUBMODULO', 'Identidad visual', NULL);

INSERT INTO permiso (id, codigo, modulo, accion, nivel, nombre, descripcion)
SELECT gen_random_uuid(), 'configuracion.identidad:' || d.accion,
       'configuracion.identidad', d.accion, 'FUNCION', d.nombre, NULL
FROM (VALUES ('consultar', 'Consultar'), ('editar', 'Editar')) AS d(accion, nombre);


-- El submodulo, a todo rol que ya alcance el modulo `configuracion`. Alcanza a
-- los roles a medida de las cuentas sin nombrarlos.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT rp.rol_id, nuevo.id
FROM rol_permiso rp
    JOIN permiso m     ON m.id = rp.permiso_id
                      AND m.nivel = 'MODULO' AND m.modulo = 'configuracion'
    JOIN permiso nuevo ON nuevo.codigo = 'configuracion.identidad:acceder'
ON CONFLICT DO NOTHING;

-- Las funciones se conceden con mas cuidado que el submodulo: quien ya podia
-- EDITAR los datos de la empresa puede editar tambien su logo, y quien solo
-- consultaba sigue solo consultando. Copiar el alcance que cada rol tiene sobre
-- `configuracion.empresa` es mas fiel que dar las dos a todos.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT rp.rol_id, nuevo.id
FROM rol_permiso rp
    JOIN permiso viejo ON viejo.id = rp.permiso_id
                      AND viejo.modulo = 'configuracion.empresa'
                      AND viejo.nivel = 'FUNCION'
    JOIN permiso nuevo ON nuevo.modulo = 'configuracion.identidad'
                      AND nuevo.nivel = 'FUNCION'
                      AND nuevo.accion = viejo.accion
ON CONFLICT DO NOTHING;
