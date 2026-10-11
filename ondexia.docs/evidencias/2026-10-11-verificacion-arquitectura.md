# ArchUnit: resolución de excepciones capturadas

Complementa ACT-23/F20; [especificación](../especificaciones/2026-10-11-verificacion-arquitectura.md),
[fuente oficial](2026-10-11-verificacion-arquitectura-fuente.json) y
[artefactos y huellas](2026-10-11-verificacion-arquitectura.json).

## Resultado local

Se actualiza únicamente `archunit.version` de 1.5.0 a 1.5.1, equivalente al
delta de Dependabot #13, adaptado sobre develop. No se incorpora su base main.

| Comprobación | Resultado |
| --- | --- |
| Primera caracterización en 1.5.0: referencia directa y prohibición SQL | 2 correctas; ya funcionaba |
| Resolución completa del tipo del catch, solo por accesos, en 1.5.0 | 3 ejecutadas; 1 fallo de aserción, 0 errores, 0 omitidas; salida 1 |
| Mismo oráculo en 1.5.1 más reglas y anotaciones de permisos | 15 correctas; salida 0 |
| Mutación aislada: campo SQLException en la clase de control general | 3 ejecutadas; 1 fallo en el control que exige ausencia de SQL; salida 1 |
| Restauración exacta con cmp; reglas y pruebas de resolución | 12 correctas; salida 0 |
| Regresión: dominio/API/panel/consultas/emisor | 170/327/41/41/77 correctas: 656 en total |

El esperado del nuevo test procede del contrato del importador y el cambio
oficial #1732, no del resultado observado. La configuración se limita al hilo
del test y se restaura por `withThreadLocalScope`. La mutación se restauró antes
de confirmar. Las fixtures no son clases productivas ni se conectan a SQL.

Comando focal, desde apps/backend, tras activar el JDK 21 de este entorno:

```sh
./mvnw -s /workspace/.ondexia-setup/maven.xml -pl ondexia.api -am \
  -Ddependency-check.skip=true -Dtest=DependenciasCapturadasTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Para las reglas se añaden `ArquitecturaTest,PermisosAnotacionIT` al selector.
La regresión usa ONDEXIA_PRUEBAS_BD_URL apuntando a 127.0.0.1:55432/postgres;
el contenedor se verificó con etiqueta `ondexia.finalidad=pruebas-sinteticas`.
Se omitió NVD en local: debe comprobarse su análisis efectivo en CI.

Una primera ejecución del reactor completo extendió por error la propiedad
`user.dir` alternativa de API al emisor: dominio/API/panel/consultas pasaron,
pero 32 pruebas del emisor dieron errores de certificados (alias existente o
lectura del archivo). Se repitió el emisor sin esa propiedad, con 77 correctas.
Es un fallo de preparación, distinto del rojo de la resolución del tipo.
Los artefactos de esas dos regresiones son resúmenes de resultados, con la
huella del registro completo local; los focales se conservan completos comprimidos.

## Límites y pendientes

No se demuestra una violación productiva ni una auditoría independiente.
La selección de resoluciones del test es sintética, no la configuración global.
CI del PR y del merge: pendientes al registrar esta evidencia; confirmar sus
resultados exactos en el PR antes de integrar/cerrar ramas. No se han ejecutado
despliegues ni operaciones SUNAT.

Braces sigue pendiente: consulta npm a 3.0.4 devuelve HTTP 404 y
`version not found: 3.0.4`. No se ha aceptado una excepción ni demostrado que
el aviso sea inexplotable. Las observaciones preliminares del lote anterior
no bastan para cerrar ese punto. ACT-23 sigue parcial; #13 no se cierra hasta
la integración equivalente y CI posterior verdes, comprobando su head actual.
