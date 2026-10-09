-- =============================================================================
-- V11 — El apellido, separado del nombre.
--
-- Hasta aqui `usuario.nombre` guardaba el nombre completo en una sola columna.
-- Se parte porque una persona se ordena, se busca y se saluda por partes
-- distintas: el listado de usuarios se ordena por apellido, y un "Hola, Luis"
-- no se puede sacar de "Luis David Vera Vilchez" sin adivinar donde termina el
-- nombre.
--
-- ADMITE NULO, Y NO SE RELLENA SOLO.
--
-- La tentacion evidente es partir los valores existentes por el primer espacio.
-- No se hace: "Luis David Vera Vilchez" daria nombre="Luis" y apellido="David
-- Vera Vilchez", y "Maria del Carmen Rojas" seria peor. Un dato inventado que
-- parece correcto es mas dificil de detectar que uno ausente, y aqui el dato
-- ausente tiene arreglo — la propia persona lo corrige la primera vez que entra
-- a Mi perfil, donde el campo ya es obligatorio.
--
-- Asi que las filas de antes conservan su nombre completo en `nombre` y
-- `apellido` en nulo. Se muestran igual que siempre, porque el nombre para
-- mostrar es la union de las dos columnas y unir con nulo no cambia nada.
--
-- Obligatorio en los formularios, opcional en la base. La restriccion vive
-- donde entra el dato nuevo, no donde vive el viejo: un NOT NULL aqui obligaria
-- a inventar un valor para cada fila existente, que es lo que se evita.
-- =============================================================================

ALTER TABLE usuario ADD COLUMN apellido varchar(150);

COMMENT ON COLUMN usuario.apellido IS
    'Nulo en las filas anteriores a la V11, que conservan el nombre completo en '
    '`nombre`. No se rellena por migracion: partir un nombre completo por el '
    'espacio inventa datos. Lo corrige su dueno desde Mi perfil.';
