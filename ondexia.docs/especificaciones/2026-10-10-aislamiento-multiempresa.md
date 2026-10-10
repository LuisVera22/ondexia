# ACT-15: primer corte de permisos y aislamiento multiempresa

Estado: ensayo local sobre develop `0df3f6d57a8b0dcece6f7667ce605c7fa307dc15`.
Alcance: PostgreSQL temporal de Testcontainers, tabla almacen y rol ondexia_app;
pruebas existentes de contexto, permisos, bitácora y grants. No cierra toda ACT-15.

## Contrato y flujo

Fuente: CLAUDE.md, aislamiento forzado por la base y contexto local a la transacción;
V1/V3 y grants vigentes, leídos sin modificarlos. No decidir nuevas políticas.

1. Crear una fila de almacén bajo empresa A con un identificador generado antes
   de las consultas, nombre y código fijados por la prueba.
2. Abrir transacción en la misma conexión como ondexia_app, sin SUPERUSER ni
   BYPASSRLS y sin propiedad de almacen; fijar contexto de empresa B.
3. Buscar, actualizar y borrar por el ID de A, sin filtro de empresa: cero filas.
4. Volver a A: fila presente, nombre original; escritura propia permitida.
5. Intentar insertar para B mientras contexto es A: rechazo SQLSTATE 42501.
6. Tras commit y rollback, reutilizar la misma conexión sin fijar empresa:
   no se accede a la fila de A. No depender de que el pool cambie de conexión.

Falta de privilegio, fallo SQL y ausencia por RLS son resultados distintos.
La escritura ajena debe rechazarse por RLS, no por campos faltantes, duplicados
o claves externas inválidas. Los IDs de A/B proceden de la semilla conocida;
no se calculan esperados consultando la respuesta actual del sistema.

## Plan y aceptación

Ejecutar cuatro suites existentes y una nueva suite SQL directa de seis casos que recorra lectura,
INSERT/UPDATE/DELETE y reutilización de conexión. Verificar explícitamente atributos
del rol y propiedad de tabla. En el servidor temporal, habilitar SET ROLE para
el propietario de pruebas y retirarlo al terminar; no modificar roles desplegados. La consulta directa elimina el filtro de empresa
de la aplicación para ejercitar la barrera de PostgreSQL.

Sensibilidad: en una transacción del contenedor de prueba, sustituir temporalmente
la política de almacen por USING(true)/WITH CHECK(true), ejecutar las mismas
aserciones de aislamiento y comprobar que detectan lectura, UPDATE, DELETE,
INSERT ajenos y acceso sin contexto en casos separados. Hacer rollback
antes de salir y comprobar la política restaurada; no editar migraciones ni tocar
base persistente. El ensayo debe distinguir la prueba intencional de mutación
de una corrección de producción: el control correcto se caracteriza, no se afirma
un defecto nuevo sin evidencia.

## Límites

Perfil local; no Cognito, RDS, IAM ni rol efectivo del despliegue. Faltan matriz de
todas las rutas/objetos de ventas, permisos revocados, caché, concurrencia y demás
tablas. La suite de grants enumera las tablas presentes: su conjunto esperado
se debe revisar en F01 para separar enumeración observada y contrato independiente.
Sin decisión nueva de negocio, API/OpenAPI, migración ni datos productivos.
