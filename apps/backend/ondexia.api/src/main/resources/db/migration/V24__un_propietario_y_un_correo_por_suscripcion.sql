-- =============================================================================
-- V24 · Un Propietario por suscripcion, y un correo que solo esta en una
-- =============================================================================
--
-- Decisiones del propietario del 2026-09-08 sobre quien es quien.
--
-- 1. UN CORREO, UNA SUSCRIPCION. El indice de la V1 era
--    `(cuenta_id, lower(email))`: el mismo correo podia figurar en dos
--    suscripciones y eran dos personas distintas para el sistema. Pasa a ser
--    unico global.
--
--    Lo que se pierde a proposito: un contador que lleva dos clientes distintos
--    necesita dos correos. Se acepta porque hace que «este correo ya esta
--    registrado» signifique siempre lo mismo, y porque el caso normal —una
--    persona con varias EMPRESAS— sigue cubierto: las empresas cuelgan de la
--    suscripcion, no del correo.
--
--    De aqui sale gratis otra regla que se pidio: el Propietario no puede
--    pertenecer a una empresa que no sea suya. `usuario.cuenta_id` es NOT NULL,
--    asi que una persona vive en una sola suscripcion; con el correo unico ya no
--    puede tener una segunda identidad en otra.
--
-- 2. UN SOLO PROPIETARIO. `cuenta_administrador` admitia varios por cuenta. Con
--    varios, «el dueño» deja de ser una persona y la transferencia de la cuenta
--    no tiene sentido. Pasa a ser uno, por UNIQUE sobre cuenta_id.
--
--    El disparador de la V1 que impide dejar una cuenta sin Propietario sigue
--    donde estaba y ahora significa exactamente eso: ni cero ni dos.
--
-- POR QUE ESTA MIGRACION PUEDE FALLAR, Y ESTA BIEN QUE FALLE. Las dos reglas
-- son mas estrictas que los datos que pudiera haber. En vez de reparar por su
-- cuenta —quedarse con el correo mas antiguo, o con el primer propietario— se
-- para y dice que hay que mirar: elegir por alguien quien pierde el acceso a su
-- cuenta no es trabajo de una migracion.
--
-- Privilegios: no se crea ninguna tabla. Los indices y restricciones no
-- necesitan GRANT.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Un correo, una suscripcion
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    repetidos text;
BEGIN
    SELECT string_agg(correo, ', ')
      INTO repetidos
      FROM (SELECT lower(email) AS correo
              FROM usuario
             GROUP BY lower(email)
            HAVING count(*) > 1) AS duplicados;

    IF repetidos IS NOT NULL THEN
        RAISE EXCEPTION
            'Hay correos en mas de una suscripcion: %. Desde la V24 un correo '
            'pertenece a una sola. Decide con el cliente cual conserva el correo '
            'y cambia el otro antes de volver a aplicar.', repetidos;
    END IF;
END $$;

DROP INDEX usuario_email_por_cuenta;

CREATE UNIQUE INDEX usuario_email_unico ON usuario (lower(email));

COMMENT ON INDEX usuario_email_unico IS
    'Un correo pertenece a una sola suscripcion. Era por cuenta hasta la V24; '
    'con varias empresas bajo la misma suscripcion el caso normal sigue '
    'cubierto, y quien de verdad trabaje para dos clientes usa dos correos.';


-- -----------------------------------------------------------------------------
-- 2. Un solo Propietario por suscripcion
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    cuentas text;
BEGIN
    SELECT string_agg(cuenta, ', ')
      INTO cuentas
      FROM (SELECT cuenta_id::text AS cuenta
              FROM cuenta_administrador
             GROUP BY cuenta_id
            HAVING count(*) > 1) AS duplicadas;

    IF cuentas IS NOT NULL THEN
        RAISE EXCEPTION
            'Estas cuentas tienen mas de un Propietario: %. Desde la V24 solo '
            'puede haber uno. Decide cual lo es y retira al resto —seguiran '
            'siendo Administradores— antes de volver a aplicar.', cuentas;
    END IF;
END $$;

ALTER TABLE cuenta_administrador DROP CONSTRAINT cuenta_administrador_unico;

ALTER TABLE cuenta_administrador ADD CONSTRAINT cuenta_administrador_unico
    UNIQUE (cuenta_id);

COMMENT ON TABLE cuenta_administrador IS
    'El Propietario de la suscripcion: uno y solo uno. Esta por encima de los '
    'roles, que son por empresa. Puede lo que el Administrador no: transferir '
    'la propiedad y dar de baja una empresa entera. El disparador de la V1 '
    'impide que una cuenta se quede sin el.';
