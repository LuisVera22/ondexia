-- =============================================================================
-- V10 — El telefono de la persona.
--
-- Es dato de CONTACTO, no de identidad. La identidad es el `cognito_sub`, y
-- quien entra lo hace con su correo: el telefono no autentica nada y no debe
-- llegar a hacerlo. Anotarlo aqui porque la tentacion existe —un dia alguien
-- querra "recuperar la cuenta por SMS"— y eso es un cambio de modelo de
-- amenazas, no un uso mas de esta columna.
--
-- Va en `usuario` y no en `empresa` a proposito: el telefono de la empresa es
-- el que sale en los comprobantes y ya vive en su ficha. Este es el del
-- individuo, lo edita solo su dueno desde Mi perfil, y ningun administrador lo
-- rellena por el — escribir el telefono de otra persona en su ficha produce un
-- dato que nadie mantiene y en el que todos confian.
--
-- Admite nulo y se queda asi. Obligarlo romperia el alta de los usuarios que ya
-- existen y el registro, que no lo pide; y un campo obligatorio que la gente no
-- quiere dar se rellena con ceros.
--
-- 30 caracteres, no 9. El formato no se valida en la base porque no hay uno
-- solo: conviven el movil peruano de nueve digitos, el fijo con codigo de area,
-- el internacional con prefijo y los anexos. Un CHECK con la forma de hoy
-- rechazaria manana al primer cliente con proveedor extranjero, y fallaria con
-- un error de restriccion que nadie sabe leer.
-- =============================================================================

ALTER TABLE usuario ADD COLUMN telefono varchar(30);

COMMENT ON COLUMN usuario.telefono IS
    'Contacto de la persona, opcional. No autentica: la identidad es cognito_sub. '
    'Lo edita su dueno desde Mi perfil, nunca un administrador.';
