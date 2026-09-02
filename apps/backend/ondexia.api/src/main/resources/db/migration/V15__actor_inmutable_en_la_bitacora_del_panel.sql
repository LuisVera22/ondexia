-- ============================================================================
-- Quien hizo cada cosa en el panel, y que nadie pueda reescribirlo.
-- ============================================================================
--
-- Hallazgo A6 de la auditoria 2026-09-01. Dos cosas, y la segunda vale poco sin
-- la primera.
--
-- ── 1. El actor se identificaba por el correo ────────────────────────────────
--
-- `auditoria_admin.actor` guarda el correo del operador, tomado del reclamo
-- `email` del token. El comentario de la V9 lo justificaba asi: «se guarda el
-- correo y no solo el sub porque esta bitacora la lee una persona». La lectura
-- es un buen motivo; el problema es el «y no solo».
--
-- El correo de una cuenta de Cognito LO CAMBIA SU DUENO. Quien haga algo que no
-- deba, cambie despues su correo y deje el anterior libre, consigue que la
-- bitacora senale a una direccion que ya no es suya — o a nadie. El `sub`, en
-- cambio, es inmutable y no se puede reasignar.
--
-- Se anaden los dos: `actor_sub` para saber QUIEN, `actor` para poder leerlo sin
-- consultar Cognito. El primero es la identidad; el segundo, una comodidad.
--
-- ── 2. La bitacora se podia modificar ────────────────────────────────────────
--
-- `auditoria` —la de los clientes— tiene desde la V1 un disparador que impide
-- UPDATE y DELETE. `auditoria_admin` no tenia ninguno, asi que el registro de lo
-- que hace NUESTRO personal sobre las cuentas de los clientes era la unica
-- bitacora del sistema que se podia reescribir. Se corrige con el mismo
-- mecanismo.
--
-- Lo comprueba BitacoraDelPanelIT.

ALTER TABLE auditoria_admin
    ADD COLUMN actor_sub varchar(80);

-- Las filas que ya existan se marcan como venidas de antes. NOT NULL despues, no
-- antes: con filas previas, un ADD COLUMN NOT NULL sin defecto falla, y poner un
-- defecto dejaria pasar en silencio las que vinieran sin sub.
UPDATE auditoria_admin SET actor_sub = 'anterior-a-v15' WHERE actor_sub IS NULL;

ALTER TABLE auditoria_admin
    ALTER COLUMN actor_sub SET NOT NULL;

COMMENT ON COLUMN auditoria_admin.actor_sub IS
    'El sub de Cognito: identidad inmutable del operador. Es lo que identifica, '
    'frente a actor que solo sirve para leer.';

COMMENT ON COLUMN auditoria_admin.actor IS
    'El correo en el momento del hecho. Comodidad de lectura: el dueno de la '
    'cuenta lo puede cambiar, asi que no identifica a nadie por si solo.';


-- -----------------------------------------------------------------------------
-- Solo inserción
-- -----------------------------------------------------------------------------
--
-- Mismo mecanismo que `impedir_modificacion_auditoria` para la bitacora de
-- clientes. Un disparador y no unicamente REVOKE porque los privilegios los
-- concede quien es dueno de la tabla y se pueden volver a conceder: el
-- disparador se aplica a todo el mundo salvo al superusuario, incluido el rol
-- del panel que es quien escribe aqui.

CREATE OR REPLACE FUNCTION impedir_modificacion_auditoria_admin()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'La bitacora del panel es de solo insercion (intento de %)', TG_OP
        USING ERRCODE = 'check_violation';
END;
$$;

CREATE TRIGGER auditoria_admin_solo_insercion
    BEFORE UPDATE OR DELETE ON auditoria_admin
    FOR EACH ROW
    EXECUTE FUNCTION impedir_modificacion_auditoria_admin();

COMMENT ON FUNCTION impedir_modificacion_auditoria_admin() IS
    'Hallazgo A6: auditoria_admin era la unica bitacora del sistema que se podia '
    'reescribir.';
