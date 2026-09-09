-- =============================================================================
-- V16 · Dos roles por defecto, y el regimen tributario de la empresa
-- =============================================================================
--
-- Plan del primer producto (doc 12 §6.1 y §3.1), decisiones del propietario del
-- 2026-09-07.
--
-- 1. ROLES. Por omision solo existen dos: el Propietario —que es
--    cuenta_administrador y no pasa por esta tabla— y el Administrador, que es
--    el rol de sistema ADMINISTRADOR. Los otros tres que sembro la V2
--    (VENDEDOR, ALMACENERO, CONTADOR) dejan de ser del sistema. Un negocio de
--    mostrador no tiene contador dentro del sistema, y un rol sembrado que nadie
--    pidio es una pregunta en cada alta de usuario. El cliente crea los que
--    necesite, en cualquier plan.
--
--    No se borran a ciegas: si alguna cuenta los tiene asignados, cada una
--    recibe una COPIA propia (mismo codigo, mismo nombre, mismos permisos) y sus
--    asignaciones se repuntan a ella. Asi nadie pierde acceso y lo que era del
--    sistema pasa a ser de la cuenta, que es exactamente lo que el producto dice
--    que deben ser esos roles. Despues se eliminan los tres del sistema;
--    rol_permiso se va en cascada.
--
-- 2. REGIMEN. El padron no informa el regimen tributario y es lo unico que
--    decide si la empresa puede emitir facturas: en el Nuevo RUS, no. Se guarda
--    en la propia empresa como una declaracion del contribuyente, con OTRO como
--    valor para todo lo que no sea el RUS —general, MYPE, especial emiten lo
--    mismo entre si—. Las empresas existentes quedan en OTRO: ninguna declaro
--    estar en el RUS porque nadie se lo pregunto, y es el caso mayoritario.
--
-- La V2 NO se toca. La semilla de esos tres roles queda alli tal como se
-- aplico, con su comentario original diciendo que un rol del sistema es
-- inmutable: era cierto cuando se escribio. Anotar el cambio en la V2 cambia
-- su checksum y rompe la validacion de Flyway en toda base que ya la tenga
-- —local, dev y la de cualquier otro—. Lo que la V2 dice de estos tres roles
-- se lee desde aqui.
--
-- Privilegios: no se crea ninguna tabla, asi que no hay nada que REVOKE. La
-- columna nueva la lee y escribe ondexia_app con los grants de `empresa`.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Roles retirados de la semilla
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    retirados constant text[] := ARRAY['VENDEDOR', 'ALMACENERO', 'CONTADOR'];
    rol_sistema record;
    cuenta_afectada record;
    copia uuid;
BEGIN
    FOR rol_sistema IN
        SELECT r.id, r.codigo, r.nombre, r.descripcion
        FROM rol r
        WHERE r.cuenta_id IS NULL AND r.codigo = ANY (retirados)
    LOOP
        FOR cuenta_afectada IN
            SELECT DISTINCT u.cuenta_id
            FROM usuario_empresa ue
                JOIN usuario u ON u.id = ue.usuario_id
            WHERE ue.rol_id = rol_sistema.id
        LOOP
            -- El codigo puede existir ya en la cuenta si alguien duplico el rol
            -- con el mismo nombre; en ese caso se reutiliza esa copia en vez de
            -- crear una segunda con el mismo codigo, que el indice rechazaria.
            SELECT r.id INTO copia
            FROM rol r
            WHERE r.cuenta_id = cuenta_afectada.cuenta_id AND r.codigo = rol_sistema.codigo;

            IF copia IS NULL THEN
                copia := gen_random_uuid();
                INSERT INTO rol (id, cuenta_id, codigo, nombre, descripcion)
                VALUES (copia, cuenta_afectada.cuenta_id, rol_sistema.codigo, rol_sistema.nombre,
                        rol_sistema.descripcion);

                INSERT INTO rol_permiso (rol_id, permiso_id)
                SELECT copia, rp.permiso_id
                FROM rol_permiso rp
                WHERE rp.rol_id = rol_sistema.id;
            END IF;

            UPDATE usuario_empresa ue
            SET rol_id = copia
            FROM usuario u
            WHERE u.id = ue.usuario_id
              AND u.cuenta_id = cuenta_afectada.cuenta_id
              AND ue.rol_id = rol_sistema.id;

            -- Invalida la cache de permisos de la cuenta: el rol de sus usuarios
            -- cambio de identidad aunque no de contenido.
            UPDATE cuenta SET permisos_version = permisos_version + 1
            WHERE id = cuenta_afectada.cuenta_id;
        END LOOP;

        DELETE FROM rol WHERE id = rol_sistema.id;
    END LOOP;
END $$;

COMMENT ON TABLE rol IS
    'Roles de la matriz de permisos. cuenta_id NULL = rol del sistema; desde la V16 '
    'el unico es ADMINISTRADOR. El Propietario no es un rol: es cuenta_administrador. '
    'Los demas roles los crea cada cuenta (doc 12 §6.1).';

-- -----------------------------------------------------------------------------
-- 2. Regimen tributario
-- -----------------------------------------------------------------------------

ALTER TABLE empresa
    ADD COLUMN regimen_tributario varchar(20) NOT NULL DEFAULT 'OTRO';

ALTER TABLE empresa
    ADD CONSTRAINT empresa_regimen_valido
        CHECK (regimen_tributario IN ('NUEVO_RUS', 'OTRO'));

-- Una persona juridica no puede estar en el Nuevo RUS. La aplicacion lo
-- rechaza con un mensaje; la base lo garantiza aunque la aplicacion no sea la
-- unica que escriba aqui.
ALTER TABLE empresa
    ADD CONSTRAINT empresa_rus_solo_persona_natural
        CHECK (regimen_tributario <> 'NUEVO_RUS' OR ruc LIKE '10%');

COMMENT ON COLUMN empresa.regimen_tributario IS
    'Declarado por el contribuyente, no por SUNAT. NUEVO_RUS no emite facturas; OTRO '
    'agrupa general, MYPE y especial, que emiten lo mismo. Doc 12 §3.1.';
