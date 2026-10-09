-- Planes con limites, modulos contratados por cuenta y el rol del panel interno.
--
-- Ver ondexia.docs/09-panel-administrativo.md, entrega 1.


-- -----------------------------------------------------------------------------
-- 1. El plan deja de ser un enumerado
-- -----------------------------------------------------------------------------
--
-- Hasta aqui cuenta.plan era un varchar con un CHECK de tres valores y los
-- limites no existian en ninguna parte: no habia forma de decir «3 de 5
-- usuarios» porque nadie sabia que el plan daba 5.
--
-- Se elige tabla y no constantes en Java por el plan «a demanda» del doc 04
-- §2.2, que es NEGOCIABLE por cliente. Un limite negociable no cabe en un
-- enumerado.

CREATE TABLE plan (
    codigo         varchar(30)  PRIMARY KEY,
    nombre         varchar(80)  NOT NULL,

    -- NULL significa sin limite, no cero. Es lo que necesita el plan a demanda.
    max_empresas   integer,
    max_usuarios   integer,

    -- Para ordenar la lista del panel sin depender del alfabeto.
    orden          smallint     NOT NULL,

    -- Un plan retirado no se borra: hay cuentas que lo tienen. Deja de
    -- ofrecerse y punto.
    activo         boolean      NOT NULL DEFAULT true,

    creado_en      timestamptz  NOT NULL DEFAULT now(),
    actualizado_en timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT plan_limites_no_negativos
        CHECK ((max_empresas IS NULL OR max_empresas > 0)
           AND (max_usuarios IS NULL OR max_usuarios > 0))
);

COMMENT ON COLUMN plan.max_empresas IS
    'Numero de RUC que admite el plan. NULL es sin limite, no cero.';

-- Los tres codigos que ya viven en cuenta.plan, con los limites del doc 04 §2.2.
-- Los nombres comerciales de ese documento (Base, Intermedio, A demanda) no
-- coinciden con estos codigos; la discrepancia se resuelve aqui, que es donde
-- ahora se puede cambiar sin migrar.
INSERT INTO plan (codigo, nombre, max_empresas, max_usuarios, orden) VALUES
    ('ESENCIAL',    'Esencial',    1,    2,    1),
    ('PROFESIONAL', 'Profesional', 2,    5,    2),
    ('CORPORATIVO', 'Corporativo', NULL, NULL, 3);


-- -----------------------------------------------------------------------------
-- 2. La cuenta apunta al plan, y puede negociar sus limites
-- -----------------------------------------------------------------------------

ALTER TABLE cuenta DROP CONSTRAINT cuenta_plan_valido;

ALTER TABLE cuenta ADD CONSTRAINT cuenta_plan_existente
    FOREIGN KEY (plan) REFERENCES plan (codigo);

-- Excepciones pactadas con un cliente concreto. NULL —lo normal— significa «lo
-- que diga el plan».
--
-- Van aqui y no en una tabla aparte porque son dos numeros por cuenta, no una
-- entidad: una tabla `limite_negociado` obligaria a un JOIN en cada comprobacion
-- para no guardar nunca mas de una fila.
ALTER TABLE cuenta ADD COLUMN limite_empresas integer;
ALTER TABLE cuenta ADD COLUMN limite_usuarios integer;

ALTER TABLE cuenta ADD CONSTRAINT cuenta_limites_no_negativos
    CHECK ((limite_empresas IS NULL OR limite_empresas > 0)
       AND (limite_usuarios IS NULL OR limite_usuarios > 0));

COMMENT ON COLUMN cuenta.limite_empresas IS
    'Limite pactado con esta cuenta. NULL usa el del plan. Ver doc 04 §2.2.';


-- -----------------------------------------------------------------------------
-- 3. Que se puede contratar
-- -----------------------------------------------------------------------------
--
-- Se apunta a una fila del catalogo de permisos de nivel MODULO o SUBMODULO, y
-- hace falta impedir que alguien apunte a una FUNCION: contratar
-- «almacen.producto:registrar» pero no «registrar» seria un modelo distinto y
-- mucho peor.
--
-- Una clave foranea no puede filtrar por columna, asi que se usa el recurso
-- clasico: se declara unica la pareja (id, nivel) en el catalogo y las tablas
-- hijas llevan su propio `nivel` con CHECK. La base rechaza el error, no el
-- codigo.

ALTER TABLE permiso ADD CONSTRAINT permiso_id_nivel_unico UNIQUE (id, nivel);

CREATE TABLE plan_modulo (
    plan_codigo varchar(30) NOT NULL REFERENCES plan (codigo) ON DELETE CASCADE,
    permiso_id  uuid        NOT NULL,
    nivel       varchar(10) NOT NULL,

    PRIMARY KEY (plan_codigo, permiso_id),

    CONSTRAINT plan_modulo_nivel_contratable
        CHECK (nivel IN ('MODULO', 'SUBMODULO')),
    CONSTRAINT plan_modulo_apunta_a_contratable
        FOREIGN KEY (permiso_id, nivel) REFERENCES permiso (id, nivel)
);

COMMENT ON TABLE plan_modulo IS
    'Lo que incluye cada plan. La ausencia de fila significa que NO se incluye.';

-- Los cuatro modulos, en los tres planes.
--
-- Hoy no diferencia nada, y es a proposito: el doc 04 §2.2 argumenta que
-- restringir Almacen, Compras o Ventas produce un producto roto —son un mismo
-- flujo de trabajo— y que la diferenciacion honesta va por piezas que no rompen
-- nada al faltar: guias de remision, roles personalizados, acceso por API.
-- Ninguna de esas existe todavia. La tabla queda lista para cuando existan.
INSERT INTO plan_modulo (plan_codigo, permiso_id, nivel)
SELECT p.codigo, m.id, m.nivel
FROM plan p
CROSS JOIN permiso m
WHERE m.nivel = 'MODULO';


-- -----------------------------------------------------------------------------
-- 4. Lo que se decide para una cuenta concreta
-- -----------------------------------------------------------------------------
--
-- Guarda DECISIONES, no el estado completo: la ausencia de fila significa «lo
-- que dicte el plan». Es el mismo idioma que la V5 uso para los tipos de
-- comprobante y por el mismo motivo — si la tabla tuviera que estar completa,
-- habria que sembrarla para cada cuenta nueva y volver a sembrarla cada vez que
-- aparezca un modulo. Con decisiones, un modulo nuevo entra por su plan sin
-- tocar una sola fila de cliente.
--
-- Sirve en los dos sentidos: apagar algo que el plan incluye, o encender algo
-- que no incluye sin cambiar de plan.

CREATE TABLE cuenta_modulo (
    cuenta_id   uuid        NOT NULL REFERENCES cuenta (id) ON DELETE CASCADE,
    permiso_id  uuid        NOT NULL,
    nivel       varchar(10) NOT NULL,
    habilitado  boolean     NOT NULL,

    -- Quien decidio esto y por que. Un modulo apagado sin motivo es una consulta
    -- de soporte garantizada dentro de seis meses.
    motivo      varchar(300),
    decidido_por varchar(200),
    decidido_en timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (cuenta_id, permiso_id),

    CONSTRAINT cuenta_modulo_nivel_contratable
        CHECK (nivel IN ('MODULO', 'SUBMODULO')),
    CONSTRAINT cuenta_modulo_apunta_a_contratable
        FOREIGN KEY (permiso_id, nivel) REFERENCES permiso (id, nivel)
);

COMMENT ON TABLE cuenta_modulo IS
    'Excepciones al plan para una cuenta. Sin fila, manda plan_modulo.';

-- Sin RLS a proposito, como cuenta y empresa: estas tablas viven por encima de
-- la frontera de empresa_id y las consulta el panel, que es de otro inquilino
-- —ninguno— por definicion.


-- -----------------------------------------------------------------------------
-- 5. La bitacora del panel es otra tabla
-- -----------------------------------------------------------------------------
--
-- No se puede reutilizar `auditoria`: tiene politica de fila por empresa_id, y
-- las acciones del panel son sobre una CUENTA. Una fila con empresa_id nulo no
-- satisface `empresa_id = empresa_actual()` y la base la rechazaria, que es
-- justo lo que se quiere de esa politica — no hay que debilitarla para meter
-- aqui algo que no es de una empresa.

CREATE TABLE auditoria_admin (
    id           uuid         PRIMARY KEY,
    cuenta_id    uuid         NOT NULL REFERENCES cuenta (id),

    -- Quien lo hizo, del grupo de personal de Cognito. Se guarda el correo y no
    -- solo el sub porque esta bitacora la lee una persona.
    actor        varchar(200) NOT NULL,
    accion       varchar(60)  NOT NULL,

    -- Antes y despues, en JSON. Nunca credenciales ni certificados (DTE §8.4).
    antes        jsonb,
    despues      jsonb,

    ocurrido_en  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX auditoria_admin_por_cuenta ON auditoria_admin (cuenta_id, ocurrido_en DESC);

COMMENT ON TABLE auditoria_admin IS
    'Cambios hechos desde el panel interno. Separada de auditoria porque su '
    'sujeto es la cuenta y no la empresa.';


-- -----------------------------------------------------------------------------
-- 6. La API de clientes deja de poder cambiar su propio plan
-- -----------------------------------------------------------------------------
--
-- La V8 concedio a ondexia_app CRUD sobre todo, cuenta incluida. Es decir: un
-- fallo en la API de clientes podia subir de plan a su propia cuenta o
-- levantarse una suspension. Nada lo explotaba; nada lo impedia.
--
-- La unica escritura real de la aplicacion sobre cuenta es incrementar
-- permisos_version para invalidar su cache de permisos, asi que quitarle el
-- UPDATE general no le quita nada que use.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ondexia_app') THEN
        RETURN;
    END IF;

    REVOKE UPDATE ON cuenta FROM ondexia_app;
    GRANT  UPDATE (permisos_version, actualizado_en) ON cuenta TO ondexia_app;

    -- Los planes son de solo lectura para la API: los necesita para saber sus
    -- limites, no para cambiarlos.
    GRANT SELECT ON plan, plan_modulo, cuenta_modulo TO ondexia_app;

    -- La bitacora del panel no es asunto suyo, ni para leer.
END
$$;


-- -----------------------------------------------------------------------------
-- 7. El rol del panel
-- -----------------------------------------------------------------------------
--
-- No se llama ondexia_admin: ese nombre ya es el del usuario maestro de RDS, que
-- es dueno de las tablas y por tanto EXENTO de las politicas de fila. Darle ese
-- nombre al panel le daria justo los privilegios que aqui se le niegan.
--
-- Igual que la V8, el bloque entero se salta cuando el rol rds_iam no existe:
-- es un rol propio de RDS y en el PostgreSQL de Testcontainers no esta. Asi la
-- migracion vale en los dos sitios sin ramas en el codigo de la aplicacion.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rds_iam') THEN
        RETURN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ondexia_panel') THEN
        CREATE ROLE ondexia_panel LOGIN;
    END IF;

    GRANT rds_iam TO ondexia_panel;
    GRANT CONNECT ON DATABASE ondexia TO ondexia_panel;
    GRANT USAGE ON SCHEMA public TO ondexia_panel;

    -- Lo que el panel puede LEER. La lista es explicita y corta a proposito:
    -- todo lo que no aparece aqui le esta negado por la base, no por el codigo.
    GRANT SELECT ON
        cuenta, plan, plan_modulo, cuenta_modulo,
        empresa, sucursal, usuario, usuario_empresa,
        rol, rol_permiso, permiso, cuenta_administrador
        TO ondexia_panel;

    -- Lo que puede ESCRIBIR.
    GRANT UPDATE (plan, estado_suscripcion, limite_empresas, limite_usuarios,
                  permisos_version, actualizado_en)
        ON cuenta TO ondexia_panel;
    GRANT INSERT, UPDATE, DELETE ON cuenta_modulo TO ondexia_panel;
    GRANT INSERT, SELECT ON auditoria_admin TO ondexia_panel;

    -- Y lo que NO se concede, que es el punto de todo esto: serie_correlativo,
    -- almacen, identidad_visual, tipo_comprobante_empresa y auditoria. El panel
    -- no puede leer los documentos tributarios de un cliente, y no porque el
    -- codigo no lo intente sino porque la base se lo niega.
END
$$;
