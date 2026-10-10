# ACT-15: primer corte de permisos y RLS

Fecha: 2026-10-10. Base de publicación develop
`c5498b35c5eff9390be980792a4e3446b90630c3`; rama
`feature/verificar-aislamiento-multiempresa`. Ver
[especificación](../especificaciones/2026-10-10-aislamiento-multiempresa.md) y
[registro de resultados y hashes](2026-10-10-aislamiento-multiempresa.json).

## Contrato contrastado

En PostgreSQL 17 temporal, ondexia_app no es superusuario, no tiene BYPASSRLS
ni es propietario de almacen. La tabla tiene RLS activo y forzado. Se comprueban
consultas por ID sin filtro de empresa: lectura, UPDATE y DELETE de otra empresa
alcanzan cero filas. INSERT para otra empresa se rechaza con SQLSTATE 42501.
La fila propia sigue accesible con su nombre esperado.

Se reutiliza una misma conexión después de commit y rollback: sin fijar empresa
no se hereda el acceso anterior. La preparación concede SET ROLE exclusivamente
al propietario del servidor temporal y lo retira después de cada caso; las
aserciones se ejecutan como ondexia_app. No equivale a conectar la aplicación
desplegada con ese rol, ni a probar permisos IAM o RDS.

## Resultados

| Fase | Casos | Fallos | Errores | Omitidos | Salida |
|---|---|---|---|---|---|
| Suite existente antes de ampliar | 17 | 0 | 0 | 0 | 0 |
| Suite final ampliada | 23 | 0 | 0 | 0 | 0 |
| Política temporal permisiva; solo seis casos SQL | 6 | 5 | 0 | 0 | 1 |
| Sin mutación, repetición final | 23 | 0 | 0 | 0 | 0 |

Los seis casos nuevos cubren atributos del rol, lectura/UPDATE/DELETE por
separado, INSERT ajeno y conexión sin contexto después de confirmar/revertir.
Los 17 anteriores corresponden a AislamientoEmpresaIT, ContextoIT,
PermisosAnotacionIT y GrantsDeLaAplicacionIT. Los oráculos proceden del contrato,
las empresas conocidas de la semilla y la fila generada antes de consultar;
no se toman cantidades ni nombres de una respuesta observada.

La mutación se habilita solo en AislamientoSqlIT con
`-Dondexia.pruebas.mutarAislamiento=true`. Cambia la política de almacen a
USING(true)/WITH CHECK(true) dentro de cada transacción. Produce cinco fallos
de aserción independientes: lectura, UPDATE, DELETE, INSERT y acceso sin
contexto. El sexto caso sigue comprobando los atributos del rol y tabla.
Cada bloque hace rollback en finally y comprueba que ambas expresiones de la
política vuelven a usar empresa_actual(); no se modifican archivos de migración.

Un ensayo previo obtuvo cuatro errores de preparación por falta de permiso para
SET ROLE. Se corrigió el soporte del ensayo en el contenedor temporal, no el
control de producción. Ese resultado se conserva y **no** se presenta como una
regresión de seguridad corregida. Después se separaron las tres operaciones
SQL para demostrar sensibilidad de cada una, sin detenerse en la primera aserción.

La ejecución inicial usó develop 0df3f6d; la repetición final usa c5498b3.
El diff entre esos commits no cambia apps/backend: el avance intermedio es
documental. Los hashes del registro corresponden a los archivos finales.

## Reproducción y límites

Desde apps/backend, excluir las tres variables ONDEXIA_PRUEBAS_BD_* de servidores
externos y activar las herramientas de esta sesión. Testcontainers crea PostgreSQL
desde postgres:17-alpine y Ryuk lo retira al terminar. No usar una base existente.

```bash
./mvnw -o -B -s /workspace/.ondexia-setup/maven.xml \
  -pl ondexia.api -am -Ddependency-check.skip=true \
  -Dtest=AislamientoSqlIT,AislamientoEmpresaIT,ContextoIT,PermisosAnotacionIT,GrantsDeLaAplicacionIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Para sensibilidad, seleccionar solo AislamientoSqlIT y habilitar la propiedad de
mutación; repetir después sin ella. Logs comprimidos con hashes en el JSON.
El análisis de dependencias local se omitió explícitamente. CI completo,
integración y auditoría independiente pendientes.

ACT-15 sigue en curso. Falta matriz de todos los objetos/rutas de ventas, cambios
de permisos/caché, concurrencia y otras tablas; la cobertura local no prueba toda
la aplicación ni la infraestructura desplegada. F01 debe revisar el oráculo de
grants que enumera tablas observadas. Este lote incorpora pruebas y evidencia,
sin cambios de lógica de producción, API/OpenAPI, Angular, datos ni migraciones.
